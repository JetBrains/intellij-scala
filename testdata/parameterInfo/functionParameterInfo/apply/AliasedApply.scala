object test {
  case class AAA(i: Int)
}

object test2 {
  import test.{AAA => BBB}

  val x = BBB(<caret>)
}
//i: Int