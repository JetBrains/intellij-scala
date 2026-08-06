import collection.mutable.{Buffer => IndexedSeq}
import collection.mutable.IndexedSeq

println(/* resolved: false */ Buffer.getClass)
println(classOf[/* resolved: false */ Buffer])

println(/* path: scala.collection.mutable.IndexedSeq, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */ IndexedSeq.getClass)
println(classOf[/* name: Reaction, path: scala.collection.mutable.IndexedSeq, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass */ IndexedSeq])
