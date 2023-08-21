def foo(using x: Int, y: Any) = x

foo(<caret>)
//TEXT: implicit x: Int, y: Any, STRIKEOUT: false