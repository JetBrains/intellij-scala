def bar(): Int = 1
bar()

def baz(): Unit = println(/*caret*/bar())
/*
1

def baz(): Unit = println(1)
*/