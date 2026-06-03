def f = {
  case class C
}

import /* resolved: false */f.C
import /* resolved: false */f.C

println( /* resolved: false */ C.getClass)
println(classOf[ /* resolved: false */ C])
