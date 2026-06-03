def foo(a: Int)(using b: Int)(using c: Int) = a

foo(<caret>)
//TEXT: (a: Int)(using b: Int)(using c: Int), STRIKEOUT: false