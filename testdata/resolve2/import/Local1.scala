object C {
  case class CC
}

import C.CC

println(/* file: Local1, offset: 24 */ CC.getClass)
println(classOf[/* file: Local1, offset: 24 */ CC])
