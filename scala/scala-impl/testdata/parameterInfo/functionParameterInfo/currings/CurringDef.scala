def foo(x: Int)(y: Int) = 3

foo(1)(<caret>)
//TEXT: (x: Int)(y: Int), STRIKEOUT: false