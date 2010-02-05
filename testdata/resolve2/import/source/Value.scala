val v = {
  case class C
}

import v./* resolved: false */C

println(/* resolved: false */ C.getClass)
println(classOf[ /* resolved: false */ C])
