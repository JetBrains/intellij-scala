object test {
  class AAA[T, S <: T](i: Int)
}

object test2 {
  import test.{AAA => BBB}

  val x = new BBB[<caret>]
}
//T, S <: T