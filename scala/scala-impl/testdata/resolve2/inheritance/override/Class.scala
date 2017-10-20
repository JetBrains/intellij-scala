class C1 {
  class C
}

class C2 extends C1 {
  class C

  println(classOf[/* line: 6 */ C])
}
