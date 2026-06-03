def foo(a: Int)(using b: Int)(implicit c: Int) = a

foo(<caret>)
//TEXT: (a: Int)(using b: Int)(implicit c: Int), STRIKEOUT: false