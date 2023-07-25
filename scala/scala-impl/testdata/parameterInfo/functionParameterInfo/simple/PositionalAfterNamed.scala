def foo(a: Any = 1, b: Any = 1) = ()
foo(a = 0, <caret>0)
//TEXT: a: Any = 1, b: Any = 1, STRIKEOUT: false