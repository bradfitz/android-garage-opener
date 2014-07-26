// Copyright 2010 Brad Fitzpatrick <brad@danga.com>
//
// See LICENSE.

package main

import (
	"crypto/hmac"
	"crypto/sha1"
	"errors"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"sync"
	"time"
)

var (
	listen = flag.String("listen", "0.0.0.0:8081", "host:port to listen on")
	altBin = flag.String("togglebin", "", "If non-empty, the alterate garage toggle binary to use.")
)

// openGarage is overridden by one of the alternate files.
var openGarage func() error

var sharedSecret string

var (
	lastOpenTime  time.Time
	lastOpenMutex sync.Mutex
)

func garageOpenError(rw http.ResponseWriter, err error) {
	fmt.Println("Error opening garage: ", err)
	rw.WriteHeader(http.StatusInternalServerError)
	fmt.Fprintf(rw, "Error opening garage: %v", err)
}

func handleGarage(rw http.ResponseWriter, req *http.Request) {
	timeString := req.FormValue("t")
	requestTime, err := strconv.ParseInt(timeString, 10, 64)
	if len(timeString) == 0 || err != nil {
		rw.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(rw, "Missing/bogus 't' query parameter.")
		return
	}

	if time.Unix(requestTime, 0).Before(time.Now().Add(-300 * time.Second)) {
		rw.WriteHeader(http.StatusForbidden)
		fmt.Fprintf(rw, "Request too old.")
		return
	}

	key := req.FormValue("key")
	if len(key) == 0 {
		rw.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(rw, "Missing 'key' query parameter.")
		return
	}

	secretHasher := hmac.New(sha1.New, []byte(sharedSecret))
	fmt.Fprint(secretHasher, timeString)
	expectedHash := fmt.Sprintf("%40x", secretHasher.Sum(nil))

	if key != expectedHash {
		rw.WriteHeader(http.StatusForbidden)
		fmt.Fprintf(rw, "Signature fail.")
		return
	}

	lastOpenMutex.Lock()
	defer lastOpenMutex.Unlock()
	now := time.Now()
	if lastOpenTime.After(now.Add(10 * time.Second)) {
		rw.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(rw, "Too soon, considering this a dup.")
		return

	}
	lastOpenTime = now

	log.Printf("Opening garage door...")
	if openGarage != nil {
		err = openGarage()
	} else {
		if *altBin == "" {
			err = errors.New("no togglebin command specified.")
		} else {
			err = exec.Command(*altBin).Run()
		}
	}
	if err != nil {
		garageOpenError(rw, err)
		return
	}

	fmt.Fprint(rw, "Opened.")
	log.Printf("Garage opened from %v\n", req.RemoteAddr)
}

func handleRoot(rw http.ResponseWriter, req *http.Request) {
	fmt.Fprintf(rw, `
Welcome to the 
<a href='http://github.com/bradfitz/android-garage-opener'>
Android garage door opener</a>
server.`)
}

func main() {
	flag.Parse()

	sharedSecret = os.Getenv("GARAGE_SECRET")
	if len(sharedSecret) == 0 {
		fmt.Fprintf(os.Stderr,
			"No GARAGE_SECRET environment variable set.\n")
		os.Exit(1)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", handleRoot)
	mux.HandleFunc("/garage", handleGarage)

	fmt.Printf("Starting to listen on http://%v/\n", *listen)
	err := http.ListenAndServe(*listen, mux)
	if err != nil {
		fmt.Fprintf(os.Stderr,
			"Error in http server: %v\n", err)
		os.Exit(1)
	}
}
