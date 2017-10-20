type A = CC

case class CC

println(/* resolved: false */ A.getClass)
println(classOf[/* file: this, offset: 5, type: org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias */ A])

