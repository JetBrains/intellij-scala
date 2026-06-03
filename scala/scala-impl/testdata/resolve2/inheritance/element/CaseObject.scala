class C1 {
  case object CO
}

class C2 extends C1 {
  println(/* line: 2 */ CO.getClass)
  println(classOf[/* resolved: false */ CO])
}