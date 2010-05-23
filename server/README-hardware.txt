=====================================
Recommended (known working) hardware:
=====================================

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

SOMEWHERE IN YOUR HOUSE:

    Computer
 (running server) <-> CM11A module <-> power ------> (x10 signal over power)


IN YOUR GARAGE:

    power --> X10 universal module ---> pair of bell wire ---> garage opener
