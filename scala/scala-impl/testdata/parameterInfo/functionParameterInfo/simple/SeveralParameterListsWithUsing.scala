def foo(a: Int)(using b: Int) = a

foo(<caret>)
//TEXT: (a: Int)(using b: Int), STRIKEOUT: false