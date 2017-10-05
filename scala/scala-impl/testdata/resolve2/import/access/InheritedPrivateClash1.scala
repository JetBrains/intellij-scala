class C {
  private case class CC
}

object O extends C {
  case class CC
}

import O.CC

println(/* */ CC.getClass)
println(classOf[/* line: 6 */ CC])