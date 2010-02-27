case class T
type T = CC

case class CC

println(/* resolved: false */ T.getClass)
println(classOf[/* resolved: false */ T])
