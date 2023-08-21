def foo(using a: Int)(b: Int)(using c: Int) = a

foo(<caret>)
//TEXT: (implicit a: Int)(b: Int)(implicit c: Int), STRIKEOUT: false