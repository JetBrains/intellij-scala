"foo" match {
  case name@Some(v: String) => {
    println( /* file: this, offset: 21, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScNamingPattern */ name.getClass)
    println(classOf[ /* resolved: false */ name])

    println( /* file: this, offset: 31, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern  */ v.getClass)
    println(classOf[ /* resolved: false */ name])
  }
}

println( /* resolved: false */ name.getClass )
println(classOf[ /* resolved: false */ name])

println( /* resolved: false */ v.getClass )
println(classOf[ /* resolved: false */ v])
