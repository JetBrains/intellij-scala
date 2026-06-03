case class C {
  def f = {}
  type A = String
}

import C./* resolved: false */f
import C./* resolved: false */A

println(/* resolved: false */ f)
println(classOf[/* resolved: false  */ f])

println(/* resolved: false */ A.getClass)
println(classOf[/* resolved: false */ A])