=====================================
Recommended (known working) hardware:
=====================================

Option 2014:

* Just buy a Raspberry Pi and a http://www.adafruit.com/products/355
  and a little breadboard (optional). All very cheap. Then the server
  (included in this repo) can control the Raspberry Pi GPIO pins to
  toggle status LEDs (optional) and the MOSFET to toggle the garage
  door.

Option 2008:

* Computer in your house (OS: Debian/Ubuntu Linux assumed, but
  any Unix where Go runs should work too, including Macs...)

* X10 universal module
  http://www.smarthome.com/2010/X10-Universal-Module/p.aspx
  http://www.x10.com/products/x10_um506.htm

* X10 CM11A serial module: (if your computer has serial ports, else get USB?)
  http://www.google.com/products?q=X10+CM11A&hl=en&aq=f

=======================
The end result will be:
=======================

Option 2014:

  Raspberry Pi plugged into Ethernet (or a wifi USB dongle) and pair
  of bell wire from your garage door opener into the MOSFET controlled
  by the Raspberry Pi's GPIO pins.

Option 2008:

SOMEWHERE IN YOUR HOUSE:

    Computer
 (running server) <-> CM11A module <-> power ------> (x10 signal over power)

IN YOUR GARAGE:

    power --> X10 universal module ---> pair of bell wire ---> garage opener
