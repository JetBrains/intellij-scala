val /*caret*/value = s"line1\nline2\nline3"
s"""$value
   |$value
   |$value
   |$value
   |""".stripMargin

value + value

s"""${1 + 2}
   |$value
   |$value
   |$value
   |$value
   |""".stripMargin
/*
"""line1
  |line2
  |line3
  |line1
  |line2
  |line3
  |line1
  |line2
  |line3
  |line1
  |line2
  |line3
  |""".stripMargin

s"line1\nline2\nline3" + s"line1\nline2\nline3"

s"""${1 + 2}
   |line1
   |line2
   |line3
   |line1
   |line2
   |line3
   |line1
   |line2
   |line3
   |line1
   |line2
   |line3
   |""".stripMargin
 */
