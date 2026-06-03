def /*caret*/foo(s: String): String =
    if (s.length > 42)
        s"""${1 + 2}
           |$s ${s} $s ${s}""".stripMargin
    else if (false)
        """unrelated
        string
    literal
      """.stripMargin
    else
        raw"""${1 + 2}
             #$s ${s} $s ${s}""".stripMargin('#')

foo("line1\nline2")(42)
/*
(if ("line1\nline2".length > 42)
  s"""${1 + 2}
     |line1
     |line2 line1
     |line2 line1
     |line2 line1
     |line2""".stripMargin
else if (false)
  """unrelated
        string
    literal
      """.stripMargin
else
  raw"""${1 + 2}
       #line1
       #line2 line1
       #line2 line1
       #line2 line1
       #line2""".stripMargin('#'))(42)
*/