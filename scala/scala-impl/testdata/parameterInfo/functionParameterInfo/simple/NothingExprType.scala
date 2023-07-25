def foo(x: Int, b: Int) = 0

foo(asdfadfa, <caret>asdfa)
//TEXT: x: Int, b: Int, STRIKEOUT: false