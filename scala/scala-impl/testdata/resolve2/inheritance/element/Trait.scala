class C1 {
  trait T
}

class C2 extends C1 {
  println(/* resolved: false */ T.getClass)
  println(classOf[/* line: 2 */ T])
}