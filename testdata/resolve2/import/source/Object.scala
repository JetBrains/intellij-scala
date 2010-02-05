object O {
  def f = {}
  type A = String
}

import O.f
import O.A

println(/* file: Object, offset: 17 */ f)
println(classOf[/* resolved: false  */ f])

println(/* resolved: false */ A.getClass)
println(classOf[/* file: Object, offset: 31 */ A])