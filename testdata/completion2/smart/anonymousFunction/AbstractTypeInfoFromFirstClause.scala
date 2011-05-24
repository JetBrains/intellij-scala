def foo[T](x: T)(y: T => Int) = 1
foo("")(/*caret*/)
/*
def foo[T](x: T)(y: T => Int) = 1
foo("")((value: String) =>/*caret*/)
*/