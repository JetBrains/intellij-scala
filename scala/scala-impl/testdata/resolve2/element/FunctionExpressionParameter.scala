(a: String, b: String) => {
  println( /* file: this, offset: 1, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ a.getClass)
  println(classOf[ /* resolved: false */ a])

  println( /* file: this, offset: 12, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ b)
  println(classOf[ /* resolved: false */ b])
}

println( /* resolved: false */ a.getClass )
println(classOf[ /* resolved: false */ a])

println( /* resolved: false */ b.getClass )
println(classOf[ /* resolved: false */ b])
