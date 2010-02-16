class P {
  private case class CC
}

class C extends P {
  println(/* resolved: false */ CC.getClass)
  println(classOf[/* resolved: false */ CC])
  println(/* resolved: false */ CC)
}