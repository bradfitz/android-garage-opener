// Copyright 2012 Brad Fitzpatrick <brad@danga.com>
//
// See LICENSE.
// Alternatively, you may use this under the same terms as Go itself.

// Package anpher provides a minimal set of APIs for interacting with
// Android.
//
// Anpher relies on a parent Dalvik process to do all the hard work.
// The parent process starts the child and they communicate speaking
// Parcels and lines over stdin and stdout.  Stderr is copied into
// Android's system log.
//
// Anpher does not intend to be provide a real Android SDK, and
// certainly isn't official. It's just enough stuff so I can use Go
// instead of Java for more parts of my garage door opener app (the
// server is already in Go).
package anpher

import (
	"bufio"
	"fmt"
	"io"
	"log"
	"os"
	"strings"
	"sync"

	"github.com/bradfitz/android-garage-opener/anpher/parcel"
)

// An App is the interface that must be implemented by an app.
// TODO: figure this out.
type App interface {
	HandleEvent(ctx *Ctx, evt Event)
}

// Ctx is the context and is used to make calls.
type Ctx struct {
	br *bufio.Reader // just for Peek debugging; use the parcel reader.
	pr *parcel.Reader
}

func (c *Ctx) Logf(format string, args ...interface{}) {
	logf(format, args...)
}

func (c *Ctx) Screenf(format string, args ...interface{}) {
	screenf(format, args...)
}

// Run runs the app.
func Run(app App) {
	ctx := &Ctx{
		br: bufio.NewReader(os.Stdin),
	}
	ctx.pr = parcel.NewReader(ctx.br)
	for {
		evt, err := ctx.readEvent()
		if err == io.EOF {
			return
		}
		if err != nil {
			log.Fatalf("ReadEvent: %v", err)
		}
		go app.HandleEvent(ctx, evt)
	}
}

type Event interface {
	String() string
	read(r *parcel.Reader) error
}

// A ClickEvent occurs when a widget is clicked.
type ClickEvent struct {
	// Id is the view ID that was clicked.
	Id int
}

func (e *ClickEvent) String() string {
	return fmt.Sprintf("Click on id %d", e.Id)
}

func (e *ClickEvent) read(r *parcel.Reader) error {
	id, err := r.ReadInt32()
	if err != nil {
		return err
	}
	e.Id = int(id)
	return nil
}

type LifecycleEvent struct {
	IsActivity bool // TODO: make this more general
	PkgName    string
	Event      string // "pause", "resume", etc
}

func (e *LifecycleEvent) String() string {
	return fmt.Sprintf("Lifecycle event %q on %q", e.Event, e.PkgName)
}

func (e *LifecycleEvent) read(r *parcel.Reader) error {
	v, err := r.ReadInt32()
	if err != nil {
		return err
	}
	e.IsActivity = (v != 0)
	e.PkgName, err = r.ReadString()
	if err != nil {
		return err
	}
	e.Event, err = r.ReadString()
	if err != nil {
		return err
	}
	return nil
}

var eventCtor = map[string]func() Event{
	"click": func() Event { return new(ClickEvent) },
	"life":  func() Event { return new(LifecycleEvent) },
}

func (ctx *Ctx) readEvent() (Event, error) {
	eventName, err := ctx.pr.ReadString()
	peekBuf, _ := ctx.br.Peek(ctx.br.Buffered())
	logf("got event name %q, %v; peek = %q", eventName, err, peekBuf)
	if err != nil {
		return nil, err
	}
	cfn, ok := eventCtor[eventName]
	if !ok {
		return nil, fmt.Errorf("Unknown event %q", eventName)
	}
	evt := cfn()
	err = evt.read(ctx.pr)
	if err != nil {
		return nil, err
	}
	return evt, nil
}

var (
	outmu sync.Mutex
	errmu sync.Mutex
)

func logf(format string, args ...interface{}) {
	errmu.Lock()
	defer errmu.Unlock()
	if !strings.HasSuffix(format, "\n") {
		format += "\n"
	}
	fmt.Fprintf(os.Stderr, format, args...)
}

func screenf(format string, args ...interface{}) {
	outmu.Lock()
	defer outmu.Unlock()
	if !strings.HasSuffix(format, "\n") {
		format += "\n"
	}
	fmt.Printf(format, args...)
}
