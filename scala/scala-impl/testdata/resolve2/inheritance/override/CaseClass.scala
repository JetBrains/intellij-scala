class C1 {
  case class C
}

class C2 extends C1 {
  case class C

  println(/* resolved: false */ C.getClass)
  println(classOf[/* resolved: false */ C])
}
