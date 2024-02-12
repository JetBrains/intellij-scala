def /*caret*/foo(param1: String, param2: Int): String =
  raw"""$param1
       #$param2
       #${param1}
       #${param2}
       #${param1 + param2}
       #$param1
       #$param2
       #${param1}
       #${param2}
       #${param1 + param2}
       #""".stripMargin('#')

foo("Hello", 42)

val forrest = "Hello"
foo(forrest, 42)
/*
raw"""Hello
     #42
     #Hello
     #42
     #${"Hello" + 42}
     #Hello
     #42
     #Hello
     #42
     #${"Hello" + 42}
     #""".stripMargin('#')

val forrest = "Hello"
raw"""$forrest
     #42
     #$forrest
     #42
     #${forrest + 42}
     #$forrest
     #42
     #$forrest
     #42
     #${forrest + 42}
     #""".stripMargin('#')
*/