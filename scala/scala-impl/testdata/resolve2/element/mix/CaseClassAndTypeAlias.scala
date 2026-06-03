case class T
type T = CC

case class CC

println(/* resolved: true */ T.getClass)
println(classOf[/* resolved: false */ T])
