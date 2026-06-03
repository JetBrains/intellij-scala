#!/bin/sh

echo Cleaning...
find -regex '.*\.\(class\|tasty\|tree\|actual\)' -delete

echo Done.
