//> expected.error cannot.inline.not.method.call
def /*caret*/foo(i: Int) = i + 1

val fun: Int => Int = foo
