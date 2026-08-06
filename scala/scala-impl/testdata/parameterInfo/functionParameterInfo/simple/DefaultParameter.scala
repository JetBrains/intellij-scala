def foo(x: Int = 45) = x

foo(<caret>)
//TEXT: x: Int = …, STRIKEOUT: false