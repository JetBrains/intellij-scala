class C {
  def f = {}
  type A = String
}

import C.f
import C.A

println(/* resolved: false */ f)
println(classOf[/* resolved: false  */ f])

println(/* resolved: false */ A.getClass)
println(classOf[/* resolved: false */ A])