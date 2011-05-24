def foo[T <: Runnable](x: (T, String) => String) = 1
foo(/*caret*/)
/*
def foo[T <: Runnable](x: (T, String) => String) = 1
foo((value: Runnable, s: String) =>/*caret*/)
*/