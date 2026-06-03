class C {
  case class CCC
}

object O extends C {
  private case class CCC
}

import O.CCC

println(/* line: 6, accessible: false */ CCC.getClass)
println(classOf[/* line: 6, accessible: false */ CCC])