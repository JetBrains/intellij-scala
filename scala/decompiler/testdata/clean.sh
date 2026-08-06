#!/bin/sh

echo Cleaning...
find -regex '.*\.\(sig\|actual\)' -delete

echo Done.
