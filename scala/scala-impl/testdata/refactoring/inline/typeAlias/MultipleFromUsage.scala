type First = Int
type Second = First

val t: First = 5
val q: /*caret*/First = t + 6
def func(first: First): Second =
  t
/*
type Second = Int

val t: Int = 5
val q: Int = t + 6
def func(first: Int): Second =
  t
*/