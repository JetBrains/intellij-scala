//> expected.error cannot.inline.function.multiple.clauses
def /*caret*/foo(i: Int)(j: Int) = ???

foo(1)(2)
