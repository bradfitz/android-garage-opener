// Copyright 2010 Brad Fitzpatrick <brad@danga.com>
//
// See LICENSE.

package main

import "crypto/hmac"
import "exec"
import "fmt"
import "http"
import "os"
import "strconv"
import "time"
import "flag"

var listen *string = flag.String("listen", "0.0.0.0:8081", "host:port to listen on")
var x10Unit *string = flag.String("x10unit", "f9", "X10 unit to toggle.")
var heyUPath *string = flag.String(
	"heyupath", "/usr/local/bin/heyu", "Path to heyu binary")

var sharedSecret string

func GarageOpenError(conn http.ResponseWriter, err os.Error) {
	fmt.Println("Error opening garage: ", err)
	conn.WriteHeader(http.StatusInternalServerError)
	fmt.Fprintf(conn, "Error opening garage: %v", err)
}

func HandleGarage(conn http.ResponseWriter, req *http.Request) {
	timeString := req.FormValue("t")
	requestTime, err := strconv.Atoi64(timeString)
	if len(timeString) == 0 || err != nil {
		conn.WriteHeader(http.StatusBadRequest)
		fmt.Fprintf(conn, "Missing/bogus 't' query parameter.")
		return
	}

	if requestTime < time.Seconds() - 60 {
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

	secretHasher := hmac.NewSHA1([]byte(sharedSecret))
	fmt.Fprint(secretHasher, timeString)
	expectedHash := fmt.Sprintf("%40x", secretHasher.Sum())

	if key != expectedHash {
		conn.WriteHeader(http.StatusForbidden)
		fmt.Fprintf(conn, "Signature fail.")
                return
	}

	fmt.Println("Opening garage door...")

	cmd, err := exec.Run(
		*heyUPath,
		[]string{"heyu", "on", *x10Unit},
		os.Environ(),
		"/",
		exec.DevNull, // stdin
		exec.DevNull, // stdout
		exec.MergeWithStdout) // stderr
	if err != nil {
		GarageOpenError(conn, err)
		return
	}

	fmt.Printf("Started heyu with pid %v\n", cmd.Pid)
	waitmsg, err := cmd.Wait(0)
	if err != nil {
		GarageOpenError(conn, err)
                return
	}
	fmt.Printf("WaitMsg: %v\n", waitmsg)

	if waitmsg.WaitStatus != 0 {
		conn.WriteHeader(http.StatusInternalServerError)
		fmt.Fprintf(conn, "x10 command returned error opening garage: %v", err)
		return
	}

	fmt.Fprint(conn, "Opened.")
	fmt.Printf("Garage opened at %v from %v\n",
		time.LocalTime(),
		conn.RemoteAddr())

}

func HandleRoot(conn http.ResponseWriter, req *http.Request) {
	fmt.Fprintf(conn, `
Welcome to the 
<a href='http://github.com/bradfitz/android-garage-opener'>
Android garage door opener</a>
server.`);
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
