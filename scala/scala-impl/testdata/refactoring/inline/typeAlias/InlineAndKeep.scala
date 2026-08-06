//> set ScalaApplicationSettings.INLINE_TYPE_ALIAS_KEEP=true
type /*caret*/My = Int
val t: My = 3
t
/*
type My = Int
val t: Int = 3
t
*/