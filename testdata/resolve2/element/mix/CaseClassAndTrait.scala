case class T
trait T

println(/* resolved: false */ T.getClass)
println(classOf[/* resolved: false */ T])

