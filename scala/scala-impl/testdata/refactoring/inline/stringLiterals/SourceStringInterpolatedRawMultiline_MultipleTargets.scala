val /*caret*/value =raw"""injected text raw \\s"""

s"""outer text ${2 + 2} $value"""
raw"""outer text ${2 + 2} $value"""
s"""outer text
   |${2 + 2}
   |$value""".stripMargin
raw"""outer text
   |${2 + 2}
   |$value""".stripMargin

"unrelated string literal should be untouched"
s"unrelated string literal should be untouched"
raw"unrelated string literal should be untouched"
"""unrelated string literal should be untouched"""
s"""unrelated string literal should be untouched"""
raw"""unrelated string literal should be untouched"""

"""unrelated string literal
  |should be untouched""".stripMargin
s"""unrelated string literal
   |should be untouched""".stripMargin
raw"""unrelated string literal
     |should be untouched""".stripMargin

"""unrelated string literal
  without margin
    should be untouched"""
s"""unrelated string literal
  without margin
    should be untouched"""
raw"""unrelated string literal
  without margin
      should be untouched"""
/*
s"""outer text ${2 + 2} injected text raw \\\\s"""
raw"""outer text ${2 + 2} injected text raw \\s"""
s"""outer text
   |${2 + 2}
   |injected text raw \\\\s""".stripMargin
raw"""outer text
     |${2 + 2}
     |injected text raw \\s""".stripMargin

"unrelated string literal should be untouched"
s"unrelated string literal should be untouched"
raw"unrelated string literal should be untouched"
"""unrelated string literal should be untouched"""
s"""unrelated string literal should be untouched"""
raw"""unrelated string literal should be untouched"""

"""unrelated string literal
  |should be untouched""".stripMargin
s"""unrelated string literal
   |should be untouched""".stripMargin
raw"""unrelated string literal
     |should be untouched""".stripMargin

"""unrelated string literal
  without margin
    should be untouched"""
s"""unrelated string literal
  without margin
    should be untouched"""
raw"""unrelated string literal
  without margin
      should be untouched"""
 */
