def foo(a: Int)(using b: Int)(using c: Int) = a

foo(<caret>)
//TEXT: (a: Int)(implicit b: Int)(implicit c: Int), STRIKEOUT: false