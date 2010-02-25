class C {
  case class CC1
}

object O extends C {
  case class CC2
}

import O.{CC1, CC2}

println(/* line: 2 */ CC1.getClass)
println(classOf[/* line: 2 */ CC1])

println(/* line: 6 */ CC2.getClass)
println(classOf[/* line: 6 */ CC2])

