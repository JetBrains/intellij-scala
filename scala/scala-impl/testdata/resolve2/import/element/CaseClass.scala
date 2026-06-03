object O {
  case class C
}

import O.C

println(/* */ C.getClass)
println(classOf[/* path: O.C, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass  */ C])
