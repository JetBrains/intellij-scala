val /*caret*/value =
  """line 1
    |line 2
    |line 3
    |""".stripMargin.trim.toString
s"""outer text 1 ${2 + 2} $value
   |outer text 2""".stripMargin
/*
s"""outer text 1 ${2 + 2} ${
  """line 1
    |line 2
    |line 3
    |""".stripMargin.trim.toString
}
   |outer text 2""".stripMargin
 */
