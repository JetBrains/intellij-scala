class C1 {
  case class CC
}

class C2 extends C1 {
  println(/* */ CC.getClass)
  println(classOf[/* line: 2 */ CC])
}