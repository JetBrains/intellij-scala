//cannot.inline.function.implicit.parameters
implicit val name: String = ???
def /*caret*/foo(implicit name: String) = ???

foo
