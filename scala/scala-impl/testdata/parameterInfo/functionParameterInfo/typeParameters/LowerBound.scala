def foo[A >: String](x: Int) = 1

foo(<caret>)
//TEXT: [A >: String](x: Int), STRIKEOUT: false