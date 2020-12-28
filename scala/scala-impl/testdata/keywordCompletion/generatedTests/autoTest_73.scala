 package x
 import a._
 class f
 class g( ) extends k {
   def foo( ) <caret>= return true
 }
// NOTE: this previousely had an empty result
// but was changed due to changes in parser SCL-18640
// Synthetic reference that is injected before `=` during completion makes it two expressions:
// 1. def foo 2. IntellijIdeaRulezz = return true
/*
do
false
for
if
new
null
return
super
this
throw
true
try
val
var
while
*/