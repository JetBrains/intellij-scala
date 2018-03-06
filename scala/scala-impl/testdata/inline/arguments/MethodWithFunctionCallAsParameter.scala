def foo(a: Int): Int = a + 1
def /*caret*/bar(a: Int): Int = a + 6
bar(foo(5))

/*
def foo(a: Int): Int = a + 1
foo(5) + 6*/