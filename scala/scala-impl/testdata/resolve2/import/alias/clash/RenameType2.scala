import collection.mutable.HashSet
import collection.mutable.{Buffer => HashSet}

println(/* resolved: false */ Buffer.getClass)
println(classOf[/* resolved: false */ Buffer])

println(/* path: scala.collection.mutable.HashSet, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */ HashSet.getClass)
println(classOf[/* path: scala.collection.mutable.HashSet */ HashSet])