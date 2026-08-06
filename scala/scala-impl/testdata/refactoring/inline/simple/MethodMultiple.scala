def /*caret*/bar(): Int = 1
bar()

def baz(): Unit = println(bar())
/*
1

def baz(): Unit = println(1)
*/