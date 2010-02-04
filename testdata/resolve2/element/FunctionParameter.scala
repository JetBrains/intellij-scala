def f(a: String, b: String) = {
    println(/* file: FunctionParameter, offset:6, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ a.getClass)
    println(/* file: FunctionParameter, offset:17, type: org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter */ b.getClass)
    println(classOf[ /* resolved: false */ a])
    println(classOf[ /* resolved: false */ b])
}