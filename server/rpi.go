// +build on_rpi

package main

import (
	"log"
	"time"

	"github.com/davecheney/gpio"
	"github.com/davecheney/gpio/rpi"
)

var red, yellow, open, button gpio.Pin

var buttonc = make(chan bool, 1)

func init() {
	openGarage = openWithGPIO

	mustPin := func(num int, mode gpio.Mode) gpio.Pin {
		pin, err := rpi.OpenPin(num, mode)
		if err != nil {
			log.Fatal("error opening pin %d for %v: %v", num, mode, err)
		}
		return pin
	}
	red = mustPin(rpi.GPIO23, gpio.ModeOutput)      // red blinky I'm-alive LED
	yellow = mustPin(rpi.GPIO22, gpio.ModeOutput)   // yellow while-pressed LED
	open = mustPin(rpi.GPIO_P1_12, gpio.ModeOutput) // to make the MOSFET close the garage circuit
	button = mustPin(rpi.GPIO25, gpio.ModeInput)    // button to toggle garage now

	if err := button.BeginWatch(gpio.EdgeBoth, onButtonUpDown); err != nil {
		log.Fatalf("button BeginWatch: %v", err)
	}
	go blinkRedHealthy()
	go waitForButtonPress()
}

func blinkRedHealthy() {
	// Signal that we're running:
	for {
		red.Set()
		time.Sleep(500 * time.Millisecond)
		red.Clear()
		time.Sleep(125 * time.Millisecond)
	}
}

func onButtonUpDown() {
	v := button.Get()
	log.Printf("Button state: %v", v)
	buttonc <- v
}

func waitForButtonPress() {
	var lastOpen time.Time
	for {
		v := <-buttonc
		if !v {
			continue
		}
		now := time.Now()
		if !lastOpen.Before(now.Add(-1250 * time.Millisecond)) {
			continue
		}
		lastOpen = now
		openWithGPIO()
	}
}

func openWithGPIO() error {
	log.Printf("Opening via GPIO pins...")
	open.Set()
	yellow.Set()
	timer := time.NewTimer(500 * time.Millisecond)
Wait:
	for {
		select {
		case <-timer.C:
			break Wait
		case <-buttonc:
			// eat events
		}
	}
	open.Clear()
	yellow.Clear()
	log.Printf("Opened.")
	return nil
}
