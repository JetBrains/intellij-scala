def foo[A : Ordering](x: Int) = 1

foo(<caret>)
//TEXT: [A: Ordering](x: Int), STRIKEOUT: false