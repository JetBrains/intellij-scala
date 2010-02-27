class C {
  case class CC
}

object O extends C {
  private case class CC
}

import O./* accessible: false */CC

println(/* line: 6, accessible: false */ CC.getClass)
println(classOf[/* line: 6, accessible: false */ CC])

