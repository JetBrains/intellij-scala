def foo[A : Ordering](x: Int)(implicit y: Long) = 1

foo(<caret>)
//[A: Ordering](x: Int)(implicit y: Long)