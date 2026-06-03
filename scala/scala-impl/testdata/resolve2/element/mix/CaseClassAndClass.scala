case class T
class T

println(/* resolved: true */ T.getClass)
println(classOf[/* resolved: false */ T])
