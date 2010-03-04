class C1 {
  case class CC
}

class C2 extends C1 {
  println(/* line: 2 */ CC.getClass)
  println(classOf[/* line: 2 */ CC])
}