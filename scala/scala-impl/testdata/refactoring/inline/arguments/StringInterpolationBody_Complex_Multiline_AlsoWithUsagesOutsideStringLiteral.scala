def /*caret*/foo(param1: String, param2: Int): String =
  param1 + param2 +
    raw"""$param1
         #$param2
         #${param1}
         #${param2}
         #${param1 + param2}
         #""".stripMargin('#') + param1 + param2 +
    s"""$param1
       |$param2
       |${param1}
       |${param2}
       |${param1 + param2}
       |""".stripMargin

foo("Hello", 42)

val forrest = "Hello"
foo(forrest, 42)
/*
"Hello" + 42 +
  raw"""Hello
       #42
       #Hello
       #42
       #${"Hello" + 42}
       #""".stripMargin('#') + "Hello" + 42 +
  s"""Hello
     |42
     |Hello
     |42
     |${"Hello" + 42}
     |""".stripMargin

val forrest = "Hello"
forrest + 42 +
  raw"""$forrest
       #42
       #$forrest
       #42
       #${forrest + 42}
       #""".stripMargin('#') + forrest + 42 +
  s"""$forrest
     |42
     |$forrest
     |42
     |${forrest + 42}
     |""".stripMargin
*/