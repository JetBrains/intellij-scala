class C(a: String, b: String) {
  println(/* file: ConstructorParameter, offset: 8, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ a.getClass)
  println(classOf[/* resolved: false */ a])

  println(/* file: ConstructorParameter, offset: 19, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ b.getClass)
  println(classOf[/* resolved: false */ b])
}