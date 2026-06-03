//> set ScalaApplicationSettings.INLINE_VARIABLE_THIS=true
val r = 33
r + /*caret*/r
r
r
/*
val r = 33
r + 33
r
r
*/