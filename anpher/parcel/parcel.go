// Copyright 2012 Brad Fitzpatrick <brad@danga.com>
//
// See LICENSE.
// Alternatively, you may use this under the same terms as Go itself.

// Package parcel partly implements Android's Parcel IPC format.
//
// See:
//   * http://developer.android.com/reference/android/os/Parcel.html
//   * frameworks/base/core/jni/android_util_Binder.cpp
//   * frameworks/base/include/binder/Parcel.h
//   * frameworks/base/libs/binder/Parcel.cpp
package parcel

import (
	"fmt"
	"io"
	"os"
	"bufio"
	"unsafe"
)

type Parcel struct {
	b []byte
}

func New() *Parcel {
	return new(Parcel)
}

func (p *Parcel) Bytes() []byte {
	return p.b
}

func (p *Parcel) WriteString(s string) {
	p.WriteInt32(int32(len(s)))
	for i := 0; i < len(s); i++ {
		p.b = append(p.b, s[i])
		p.b = append(p.b, 0)
	}
}

func (p *Parcel) align(n int) {
	mod := len(p.b) % n
	if mod == 0 {
		return
	}
	for i := 0; i < (n - mod); i++ {
		p.b = append(p.b, 0)
	}
}

func (p *Parcel) WriteInt32(v int32) {
	p.align(4)
	var buf [4]byte
	*(*int32)(unsafe.Pointer(&buf[0])) = v
	p.b = append(p.b, buf[:]...)
}

func NewReader(br *bufio.Reader) *Reader {
	return &Reader{br: br}
}

type Reader struct {
	br   *bufio.Reader
	read int
}

func (r *Reader) align(size int) error {
	mod := r.read % size
	if mod == 0 {
		return nil
	}
	for i := 0; i < size-mod; i++ {
		_, err := r.br.ReadByte()
		if err != nil {
			return err
		}
		r.read++
	}
	return nil
}

func (r *Reader) ReadInt32() (int32, error) {
	if err := r.align(4); err != nil {
		return 0, err
	}
	var buf [4]byte
	_, err := io.ReadFull(r.br, buf[:4])
	if err != nil {
		return 0, err
	}
	r.read += 4
	v := *(*int32)(unsafe.Pointer(&buf[0]))
	return v, nil
}

func (r *Reader) ReadString() (string, error) {
	size, err := r.ReadInt32()
	if err != nil {
		return "", err
	}
	if size == -1 {
		// Map java null strings to the empty string.
		return "", nil
	}
	if size > 1<<20 {
		fmt.Fprintf(os.Stderr, "bug? large string being received: %d bytes\n", size)
	}
	var buf = make([]byte, size+1)
	for i := range buf {
		b1, err := r.br.ReadByte()
		if err != nil {
			return "", err
		}
		b2, err := r.br.ReadByte()
		if err != nil {
			return "", err
		}
		r.read += 2
		if b1 != 0 {
			buf[i] = b1
		} else {
			buf[i] = b2
		}
	}
	if buf[size] != 0 {
		return "", fmt.Errorf("ReadString string wasn't null terminated: %q", buf[:size])
	}
	return string(buf[:size]), nil
}
