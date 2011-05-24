def foo[T](x: (T, String) => String) = 1
foo(/*caret*/)
/*
def foo[T](x: (T, String) => String) = 1
foo((value: T, s: String) =>/*caret*/)
*/