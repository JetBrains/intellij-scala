def foo(c: (Int, Int, Int, Int) => Int) = 1
foo(/*caret*/)
/*
def foo(c: (Int, Int, Int, Int) => Int) = 1
foo((i: Int, i0: Int, i1: Int, i2: Int) =>/*caret*/)
*/