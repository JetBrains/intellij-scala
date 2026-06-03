def f {}
case class A

println(super./* resolved: false */f)

println(super./* resolved: false */A.getClass)
println(classOf[super./* resolved: false */A])

