val /*caret*/value =s"line1\nline2\nline3"
s"""outer text ${2 + 2} $value ${3 + 3} $value
   |$value $value
   |outer text
   |$value""".stripMargin
/*
s"""outer text ${2 + 2} line1
   |line2
   |line3 ${3 + 3} line1
   |line2
   |line3
   |line1
   |line2
   |line3 line1
   |line2
   |line3
   |outer text
   |line1
   |line2
   |line3""".stripMargin
 */
