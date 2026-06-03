//> set ScalaApplicationSettings.INLINE_TYPE_ALIAS_THIS=true
type First = Int
type Second = First

val t: /*caret*/First = 5
val q: First = t + 6
def func(first: First): Second =
  t
/*
type First = Int
type Second = First

val t: Int = 5
val q: First = t + 6
def func(first: First): Second =
  t
*/