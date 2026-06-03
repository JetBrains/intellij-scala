"foo" match {
  case (a: String, b: String) => {
    println( /* file: this, offset: 22, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ a.getClass)
    println(classOf[ /* resolved: false */ a])

    println( /* file: this, offset: 33, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ b.getClass)
    println(classOf[ /* resolved: false */ b])
  }
}

println( /* resolved: false */ a.getClass )
println(classOf[ /* resolved: false */ a])

println( /* resolved: false */ b.getClass )
println(classOf[ /* resolved: false */ b])
