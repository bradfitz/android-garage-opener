#!/usr/bin/perl

use strict;
use File::Path qw(make_path);

die "This script is meant to be run within the danga/garagebuild Docker contain. Run 'make env' to build it.\n"
    unless $ENV{IN_DOCKER};

my $mode = shift || "debug";

my $ANDROID = "/src/android-garage-opener";

print "Running ant $mode\n";
chdir $ANDROID or die "can't cd to android dir";
exec "ant",
    "-Dsdk.dir=/usr/local/android-sdk-linux",
    "-Dkey.store=/keys/android-release-garagekey.keystore",
    "-Dkey.alias=garagekey",
    $mode;
