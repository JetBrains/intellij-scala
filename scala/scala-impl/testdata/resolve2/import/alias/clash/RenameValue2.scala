import collection.mutable.Buffer
import collection.mutable.{ArrayBuffer => Buffer}

println(/* resolved: false */ ArrayBuffer.getClass)
println(classOf[/* resolved: false */ ArrayBuffer])

println(/* path: scala.collection.mutable.Buffer, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */ Buffer.getClass)
println(classOf[/* path: scala.collection.mutable.Buffer */ Buffer])