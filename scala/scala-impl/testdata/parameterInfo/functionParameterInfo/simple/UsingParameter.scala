def foo(using x: Int, y: Any) = x

foo(<caret>)
//TEXT: using x: Int, y: Any, STRIKEOUT: false