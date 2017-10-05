case class CC

type A = CC
type A = ÑÑ

println(/* resolved: false */ A.getClass)
println(classOf[/* resolved: false */ A])

