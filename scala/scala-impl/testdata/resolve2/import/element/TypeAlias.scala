object O {
  type A = String
}

import O.A

println(/* resolved: false  */ A.getClass)
println(classOf[/* file: this, offset: 18 */ A])