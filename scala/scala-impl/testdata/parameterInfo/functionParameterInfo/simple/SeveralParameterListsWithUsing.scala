def foo(a: Int)(using b: Int) = a

foo(<caret>)
//TEXT: (a: Int)(implicit b: Int), STRIKEOUT: false