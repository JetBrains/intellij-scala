case class C(p: String)

import C./* resolved: false */p

println(/* resolved: false */ p.getClass)
println(classOf[/* resolved: false */ p])