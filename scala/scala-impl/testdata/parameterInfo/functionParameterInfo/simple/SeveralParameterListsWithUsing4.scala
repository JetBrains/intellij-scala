def foo(using a: Int)(b: Int)(using c: Int) = a

foo(<caret>)
//TEXT: (using a: Int)(b: Int)(using c: Int), STRIKEOUT: false