val /*caret*/value =
  """line 1
    |line 2
    |line 3
    |""".stripMargin
s"""outer text ${2 + 2} $value"""
/*
s"""outer text ${2 + 2} line 1
   |line 2
   |line 3
   |""".stripMargin
 */
