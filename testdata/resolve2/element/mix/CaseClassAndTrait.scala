case class T
trait T

println(/* resolved: true */ T.getClass)
println(classOf[/* resolved: false */ T])

