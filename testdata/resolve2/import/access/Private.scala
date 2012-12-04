import scala.reflect.macros.internal./* */macroImpl

println(/* resolved: false */ macroImpl.getClass)
println(classOf[/* accessible: false */ macroImpl])