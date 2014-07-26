// +build with_x10

package main

import (
	"flag"
	"os/exec"
)

var (
	x10Unit  = flag.String("x10unit", "f9", "X10 unit to toggle.")
	heyUPath = flag.String("heyupath", "/usr/local/bin/heyu", "Path to heyu binary")
)

func init() {
	openGarage = openWithX10
}

func openWithX10() error {
	return exec.Command(*heyUPath, "on", *x10Unit).Run()
}
