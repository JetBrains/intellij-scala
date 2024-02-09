val /*caret*/value ="line 1\nline2\nline3"
class A {
  class B {
    s"""outer text ${2 + 2} $value
       |outer line 2""".stripMargin
  }
}
/*
class A {
  class B {
    s"""outer text ${2 + 2} line 1
       |line2
       |line3
       |outer line 2""".stripMargin
  }
}
 */
