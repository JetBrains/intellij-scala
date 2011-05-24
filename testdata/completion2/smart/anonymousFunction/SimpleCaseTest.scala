def foo(x: String => String) = 1
foo {/*caret*/}
/*
def foo(x: String => String) = 1
foo {case s: String =>/*caret*/}
*/