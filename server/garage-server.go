package main

import "crypto/hmac"
import "fmt"
import "http"
import "strconv"
import "time"

func HandleGarage(conn *http.Conn, req *http.Request) {
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

	secretHasher := hmac.NewSHA1([]byte("foo"))
	secretHasher.Reset() // redundant
	fmt.Fprint(secretHasher, timeString)
	expectedHash := fmt.Sprintf("%40x", secretHasher.Sum())

	if key != expectedHash {
		conn.WriteHeader(http.StatusForbidden)
		fmt.Fprintf(conn, "Signature fail.")
                return
	}

	fmt.Fprint(conn, "Opened.")
	fmt.Println("Opened garage door.")
}

func HandleRoot(conn *http.Conn, req *http.Request) {
	fmt.Fprintf(conn, `
Welcome to the 
<a href='http://github.com/bradfitz/android-garage-opener'>
Android garage door opener</a>
server.`);
}

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/", HandleRoot)
	mux.HandleFunc("/garage", HandleGarage)
	http.ListenAndServe("0.0.0.0:8001", mux)
}
