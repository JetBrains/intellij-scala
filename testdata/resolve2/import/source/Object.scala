object O {
  def f = {}
  type A = String
}

import O.f
import O.A

println(/* file: this, offset: 17 */ f)
println(classOf[/* resolved: false  */ f])

println(/* resolved: false */ A.getClass)
println(classOf[/* file: this, offset: 31 */ A])