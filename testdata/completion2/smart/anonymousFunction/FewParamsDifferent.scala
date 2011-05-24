def foo(x: (Int, String, Int, String) => Int) = 1
foo(/*caret*/)
/*
def foo(x: (Int, String, Int, String) => Int) = 1
foo((i: Int, s: String, i0: Int, s0: String) =>/*caret*/)
*/