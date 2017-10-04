class P {
  private case class CC
}

class C extends P {
  println(/* accessible: false */ CC.getClass)
  println(classOf[/* accessible: false */ CC])
  println(/* accessible: false */ CC)
}