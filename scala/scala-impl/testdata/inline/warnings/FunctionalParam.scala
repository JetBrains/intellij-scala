//cannot.inline.function.functional.parameters
def /*caret*/foo(x: Int, p: Int => Int) = p(x)

foo(1, _ + 1)