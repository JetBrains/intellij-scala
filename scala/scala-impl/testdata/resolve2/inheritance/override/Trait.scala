class C1 {
  trait T
}

class C2 extends C1 {
  trait T

  println(classOf[/* line: 6 */ T])
}
