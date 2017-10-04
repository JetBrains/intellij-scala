def f[A, B] = {
  println(/* resolved: false */ A.getClass)
  var vA: /* offset: 17, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam */ A = null.asInstanceOf[A]
  println(classOf[/* resolved: false */ A])

  println(/* resolved: false */ B.getClass)
  var vB: /* offset: 20, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam */ B = null.asInstanceOf[B]
  println(classOf[/* resolved: false */ B])
}