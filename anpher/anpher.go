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
	"time"

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

	outmu sync.Mutex
	errmu sync.Mutex

	mu sync.Mutex
	cb map[int]func(Event)
}

func (c *Ctx) Logf(format string, args ...interface{}) {
	if !strings.HasSuffix(format, "\n") {
		format += "\n"
	}
	c.errmu.Lock()
	defer c.errmu.Unlock()
	fmt.Fprintf(os.Stderr, format, args...)
}

func (c *Ctx) Screenf(format string, args ...interface{}) {
	if !strings.HasSuffix(format, "\n") {
		format += "\n"
	}
	c.outmu.Lock()
	defer c.outmu.Unlock()
	fmt.Printf(format, args...)
}

func (c *Ctx) writeParcel(p *parcel.Parcel) {
	c.outmu.Lock()
	defer c.outmu.Unlock()
	b := p.Bytes()
	_, err := fmt.Printf("PARCEL:%d\n", len(b))
	check(err)
	_, err = os.Stdout.Write(b)
	check(err)
}

func check(e error) {
	if e != nil {
		panic(e)
	}
}

func (c *Ctx) addQueryCallback(txId int, cb func(Event)) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.cb == nil {
		c.cb = make(map[int]func(Event))
	}
	c.cb[txId] = cb
}

func (c *Ctx) queryCallback(txId int) func(Event) {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.cb[txId]
}

func (c *Ctx) query(txId int, p *parcel.Parcel, cb func(Event)) {
	donec := make(chan bool)
	c.addQueryCallback(txId, func(e Event) {
		cb(e)
		c.mu.Lock()
		delete(c.cb, txId)
		c.mu.Unlock()
		donec <- true
	})
	c.writeParcel(p)
	<-donec
}

var (
	idMu   sync.Mutex
	nextId = int(time.Now().Unix()) // minor protection against child restarting and reusing same ids
)

// nextTxId allocates a new transaction id for doing a query to the parent.
func nextTxId() int {
	idMu.Lock()
	defer idMu.Unlock()
	nextId++
	return nextId
}

func (c *Ctx) FindViewId(name string) (id int, ok bool) {
	txId := nextTxId()
	p := parcel.New()
	p.WriteString("getResId")
	p.WriteString("name")
	p.WriteInt32(int32(id))

	c.query(txId, p, func(evt Event) {

	})
	return
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

		if resEvent, ok := evt.(replyEvent); ok {
			txId := resEvent.txId()
			fn := ctx.queryCallback(txId)
			go fn(evt)
			continue
		}

		// TODO: process events serially in one separate goroutine, not one
		// per event.
		go app.HandleEvent(ctx, evt)
	}
}

type Event interface {
	String() string
	read(r *parcel.Reader) error
}

type replyEvent interface {
	Event
	txId() int
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
	ctx.Logf("got event name %q, %v; peek = %q", eventName, err, peekBuf)
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
