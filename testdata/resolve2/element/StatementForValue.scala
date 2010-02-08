for (v: String <- List[String]()) {
  println(/* file: StatementForValue, offset: 5, length: 9, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ v.getClass)
  println(classOf[/* resolved: false */ v])
}                     

println( /* resolved: false */ v.getClass )
println(classOf[ /* resolved: false */ v])
