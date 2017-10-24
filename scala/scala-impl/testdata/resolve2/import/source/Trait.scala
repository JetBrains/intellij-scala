trait T {
  def f = {}
  type A = String
}

import /* resolved: false */T.f
import /* resolved: false */T.A

println(/* resolved: false  */ f)
println(classOf[/* resolved: false  */ f])

println(/* resolved: false */ A.getClass)
println(classOf[/* resolved: false  */ A])