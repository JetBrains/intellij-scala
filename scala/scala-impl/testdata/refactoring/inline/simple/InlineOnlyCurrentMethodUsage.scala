//> set ScalaApplicationSettings.INLINE_METHOD_THIS=true
def bar(): Int = 1
bar()

def baz(): Unit = println(/*caret*/bar())
/*
def bar(): Int = 1
bar()

def baz(): Unit = println(1)
*/