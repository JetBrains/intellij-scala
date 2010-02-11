abstract class C[A, B] {
  println(/* resolved: false */ A.getClass)
  val vA: /* offset: 17, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam */ A
  println(classOf[/* resolved: false */ A])

  println(/* resolved: false */ B.getClass)
  val vB: /* offset: 20, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam */ B
  println(classOf[/* resolved: false */ B])
}