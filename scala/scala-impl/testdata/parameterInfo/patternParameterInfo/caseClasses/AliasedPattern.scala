object test {
  case class AAA(i: Int)
}

object test2 {
  import test.{AAA => BBB}

  val x = BBB(1)
  x match {
    case BBB(<caret>)
  }
}
//i: Int