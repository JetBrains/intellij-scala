def foo(a: Int)(b: Int) = a

foo(<caret>)
//TEXT: (a: Int)(b: Int), STRIKEOUT: false