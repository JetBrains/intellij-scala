//> set ScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER=false
val /*caret*/value ="line 1\nline2\nline3"
s"""outer text ${2 + 2} $value""".stripMargin('#')
/*
s"""outer text ${2 + 2} line 1
   #line2
   #line3""".stripMargin('#')
 */
