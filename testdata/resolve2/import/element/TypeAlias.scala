object O {
  type A = String
}

import O.A

println(/* resolved: false  */ A.getClass)
println(classOf[/* file: TypeAlias, offset: 18 */ A])