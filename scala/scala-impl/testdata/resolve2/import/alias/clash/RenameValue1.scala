import collection.mutable.{HashSet => HashMap}
import collection.mutable.HashMap

println(/* resolved: false */ HashSet.getClass)
println(classOf[/* resolved: false */ HashSet])

println(/* name: Futures, path: scala.collection.mutable.HashMap, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */ HashMap.getClass)
println(classOf[/* path: scala.collection.mutable.HashMap */ HashMap])