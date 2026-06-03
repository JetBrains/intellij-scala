def foo(a: Int)(implicit b: Int) = a

foo(<caret>)
//TEXT: (a: Int)(implicit b: Int), STRIKEOUT: false