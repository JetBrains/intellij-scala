for ((a: String, b: String) <- Map[String, String]()) {
  println(/* file: StatementForBinding, offset: 6, length: 9, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ a.getClass)
  println(classOf[/* resolved: false */ a])

  println(/* file: StatementForBinding, offset: 17, length: 9, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ b.getClass)
  println(classOf[/* resolved: false */ b])
}

println( /* resolved: false */ a.getClass )
println(classOf[ /* resolved: false */ a])

println( /* resolved: false */ b.getClass )
println(classOf[ /* resolved: false */ b])
