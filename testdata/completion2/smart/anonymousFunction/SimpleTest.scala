def foo(x: String => String) = 1
foo(/*caret*/)
/*
def foo(x: String => String) = 1
foo((s: String) =>/*caret*/)
*/