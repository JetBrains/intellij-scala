def /*caret*/foo(param1: String, param2: Int): String = s"$param1, $param2, ${param1}, ${param2}"

foo("Hello", 42)

val forrest = "Hello"
foo(forrest, 42)
/*
"Hello, 42, Hello, 42"

val forrest = "Hello"
s"$forrest, 42, $forrest, 42"
*/