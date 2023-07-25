def foo(a: Int)(b: Int) = a

foo(2)(<caret>)
//TEXT: (a: Int)(b: Int), STRIKEOUT: false