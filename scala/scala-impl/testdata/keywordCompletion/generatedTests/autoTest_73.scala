 package x
 import scala._
 class f
 class g( ) {
   def foo( ) <caret>= return true
 }
// NOTE: this previousely had an empty result
// but was changed due to changes in parser SCL-18640
// Synthetic reference that is injected before `=` during completion makes it two expressions:
// 1. def foo 2. IntellijIdeaRulezz = return true
//
// !! UPDATE: IT NOW WORKS AGAIN
/*
*/