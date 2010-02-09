class C(a: String, b: String) {
  println(/* file: this, offset: 8, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ a.getClass)
  println(classOf[/* resolved: false */ a])

  println(/* file: this, offset: 19, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ b.getClass)
  println(classOf[/* resolved: false */ b])
}