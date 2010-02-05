def f = {
  case class C
}

import f.C
import f.C

println( /* resolved: false */ C.getClass)
println(classOf[ /* resolved: false */ C])
