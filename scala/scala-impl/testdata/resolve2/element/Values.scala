val a, b = "foo"

println(/* file: this, offset: 4, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern */ a.getClass)
println(classOf[ /* resolved: false */ a])

println(/* file: this, offset: 7, type: org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern */ b.getClass)
println(classOf[ /* resolved: false */ b])
