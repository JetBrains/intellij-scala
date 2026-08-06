val /*caret*/value =s"injected text interpolated line1\nline2\nline3"
s"""outer text ${2 + 2} $value"""
/*
s"""outer text ${2 + 2} injected text interpolated line1
   |line2
   |line3""".stripMargin
 */
