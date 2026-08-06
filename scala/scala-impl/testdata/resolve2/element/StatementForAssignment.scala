for (a: String <- List[String]();
     val b: String = a.toString) {
  println( /* file: this, offset: 5, length: 9, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ a.getClass)
  println(classOf[ /* resolved: false */ a])

  println( /* file: this, offset: 43, length: 9, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ b.getClass)
  println(classOf[ /* resolved: false */ b])
}

println( /* resolved: false */ a.getClass )
println(classOf[ /* resolved: false */ a])

println( /* resolved: false */ b.getClass )
println(classOf[ /* resolved: false */ b])
