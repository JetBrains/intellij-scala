def /*caret*/bar(a: Int, b: Int, c: String): String = ((a + b) * b).toString + c
bar(4, 5, "foo")

//((4 + 5) * 5).toString + "foo"