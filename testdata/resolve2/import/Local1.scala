object C {
  case class CC
}

import C.CC

println(/* file: this, offset: 24 */ CC.getClass)
println(classOf[/* file: this, offset: 24 */ CC])
