def foo[T >: Int](x: (T, String) => String) = 1
foo(/*caret*/)
/*
def foo[T >: Int](x: (T, String) => String) = 1
foo((value: Int, s: String) =>/*caret*/)
*/