class C {
  private case class CC1
}

object O extends C {
  case class CC2
}

import O.{CC1, CC2}

println(/* accessible: false */ CC1.getClass)
println(classOf[/* line: 2, accessible: false */ CC1])

println(/* */ CC2.getClass)
println(classOf[/* line: 6 */ CC2])

