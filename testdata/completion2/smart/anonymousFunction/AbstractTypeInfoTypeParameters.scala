def foo[T <: Runnable](x: T => String) = 1
class X extends Runnable
foo[X](/*caret*/)
/*
def foo[T <: Runnable](x: T => String) = 1
class X extends Runnable
foo[X]((x: X) =>/*caret*/)
*/