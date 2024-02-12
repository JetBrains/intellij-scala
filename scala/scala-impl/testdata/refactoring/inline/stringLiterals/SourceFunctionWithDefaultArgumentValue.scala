def /*caret*/foo(x: String = "default"): Int = x.length + 42

s"""${foo("11")}
   |${foo("22")}
   |${foo("33")}""".stripMargin

s"""${foo()}
   |${foo()}
   |${foo()}""".stripMargin

foo("11")
foo("22")
foo("33")

foo()
foo()
foo()
/*
s"""${"11".length + 42}
   |${"22".length + 42}
   |${"33".length + 42}""".stripMargin

s"""${"default".length + 42}
   |${"default".length + 42}
   |${"default".length + 42}""".stripMargin

"11".length + 42
"22".length + 42
"33".length + 42

"default".length + 42
"default".length + 42
"default".length + 42
 */