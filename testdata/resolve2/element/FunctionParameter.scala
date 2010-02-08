def f(a: String, b: String) = {
  println( /* file: FunctionParameter, offset:6, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ a.getClass)
  println(classOf[ /* resolved: false */ a])

  println( /* file: FunctionParameter, offset:17, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ b.getClass)
  println(classOf[ /* resolved: false */ b])
}

println( /* resolved: false */ a.getClass )
println(classOf[ /* resolved: false */ a])

println( /* resolved: false */ b.getClass )
println(classOf[ /* resolved: false */ b])
