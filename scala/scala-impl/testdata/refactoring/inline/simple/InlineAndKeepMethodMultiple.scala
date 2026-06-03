//> set ScalaApplicationSettings.INLINE_METHOD_KEEP=true
def /*caret*/bar(): Int = 1
bar()

def baz(): Unit = println(bar())
/*
def bar(): Int = 1
1

def baz(): Unit = println(1)
*/