// Copyright 2010 Brad Fitzpatrick <brad@danga.com>
//
// See LICENSE.

package main

import (
	"crypto/hmac"
	"crypto/sha1"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"sync"
	"time"
)

var (
	listen   = flag.String("listen", "0.0.0.0:8081", "host:port to listen on")
	x10Unit  = flag.String("x10unit", "f9", "X10 unit to toggle.")
	heyUPath = flag.String(
		"heyupath", "/usr/local/bin/heyu", "Path to heyu binary")
)

var sharedSecret string

var lastOpenTime time.Time
var lastOpenMutex sync.Mutex

func GarageOpenError(conn http.ResponseWriter, err error) {
	fmt.Println("Error opening garage: ", err)
	conn.WriteHeader(http.StatusInternalServerError)
	fmt.Fprintf(conn, "Error opening garage: %v", err)
}

func HandleGarage(conn http.ResponseWriter, req *http.Request) {
	timeString := req.FormValue("t")
	requestTime, err := strconv.ParseInt(timeString, 10, 64)
	if len(timeString) == 0 || err != nil {
		conn.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(conn, "Missing/bogus 't' query parameter.")
		return
	}

	if time.Unix(requestTime, 0).Before(time.Now().Add(-60 * time.Second)) {
		conn.WriteHeader(http.StatusForbidden)
		fmt.Fprintf(conn, "Request too old.")
		return
	}

	key := req.FormValue("key")
	if len(key) == 0 {
		conn.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(conn, "Missing 'key' query parameter.")
		return
	}

	secretHasher := hmac.New(sha1.New, []byte(sharedSecret))
	fmt.Fprint(secretHasher, timeString)
	expectedHash := fmt.Sprintf("%40x", secretHasher.Sum(nil))

	if key != expectedHash {
		conn.WriteHeader(http.StatusForbidden)
		fmt.Fprintf(conn, "Signature fail.")
		return
	}

	lastOpenMutex.Lock()
	defer lastOpenMutex.Unlock()
	now := time.Now()
	if lastOpenTime.After(now.Add(10 * time.Second)) {
		conn.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(conn, "Too soon, considering this a dup.")
		return
	}
	lastOpenTime = now

	fmt.Println("Opening garage door...")
	err = exec.Command(*heyUPath, "on", *x10Unit).Run()
	if err != nil {
		GarageOpenError(conn, err)
		return
	}

	fmt.Fprint(conn, "Opened.")
	fmt.Printf("Garage opened at %v from %v\n",
		time.Now(),
		req.RemoteAddr)

}

func HandleRoot(conn http.ResponseWriter, req *http.Request) {
	fmt.Fprintf(conn, `
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
	mux.HandleFunc("/", HandleRoot)
	mux.HandleFunc("/garage", HandleGarage)

	fmt.Printf("Starting to listen on http://%v/\n", *listen)
	err := http.ListenAndServe(*listen, mux)
	if err != nil {
		fmt.Fprintf(os.Stderr,
			"Error in http server: %v\n", err)
		os.Exit(1)
	}
}
