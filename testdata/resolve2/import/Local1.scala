object C {
  case class CC
}

import C.CC

println(/* file: Local, offset: 23 */ CC.getClass)
println(classOf[/* file: Local, offset: 24 */ CC])
