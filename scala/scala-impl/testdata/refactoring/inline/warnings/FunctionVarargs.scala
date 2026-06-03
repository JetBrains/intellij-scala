//> expected.error cannot.inline.function.varargs
def foo(s: String, ints: Int*) = ???

foo("bar", 1, 2, 3)
