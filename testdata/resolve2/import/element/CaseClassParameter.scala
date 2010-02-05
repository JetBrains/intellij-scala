case class C(p: String)

import C.p

println(/* resolved: false */ p.getClass)
println(classOf[/* resolved: false */ p])