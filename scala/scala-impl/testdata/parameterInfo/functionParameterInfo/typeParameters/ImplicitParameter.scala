def foo[A : Ordering](x: Int)(implicit y: Long) = 1

foo(<caret>)
//TEXT: [A: Ordering](x: Int)(implicit y: Long), STRIKEOUT: false