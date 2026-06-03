def /*caret*/bar(a: Int, b: Int = 100, c: Int = 200, d: Int = 300, e: Int): Int = a + b + c + d + e
bar(5, e = 400, c = 201)

//5 + 100 + 201 + 300 + 400