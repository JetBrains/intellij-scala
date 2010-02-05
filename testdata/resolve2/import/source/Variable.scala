var v = {
  case class C
}

import v.C
import v.C

println( /* resolved: false */ C.getClass)
println(classOf[ /* resolved: false */ C])
