def foo(x: Int) = 1
def foo(x: Boolean) = 2
foo(<caret>)
/*
TEXT: x: Boolean, STRIKEOUT: false
TEXT: x: Int, STRIKEOUT: false
*/