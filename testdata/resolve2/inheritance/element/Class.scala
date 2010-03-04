class C1 {
  class C
}

class C2 extends C1 {
  println(/* resolved: false */ C.getClass)
  println(classOf[/* line: 2 */ C])
}