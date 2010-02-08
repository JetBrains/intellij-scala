"foo" match {
  case s: String => {
    println( /* file: CaseClauseParameter, offset: 21, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern */ s.getClass)
    println(classOf[ /* resolved: false */ s])
  }
}

println( /* resolved: false */ s.getClass )
println(classOf[ /* resolved: false */ s])
