val /*caret*/value =
  s"""line 1
    |${2 + 2}
    |line 2
    |""".stripMargin
s"""outer text ${2 + 2} $value"""
/*
s"""outer text ${2 + 2} line 1
   |${2 + 2}
   |line 2
   |""".stripMargin
 */
