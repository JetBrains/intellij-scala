val /*caret*/value ="line1\nline2\nline3"
s"""$value
   |$value
   |$value""".stripMargin
/*
"""line1
  |line2
  |line3
  |line1
  |line2
  |line3
  |line1
  |line2
  |line3""".stripMargin
 */
