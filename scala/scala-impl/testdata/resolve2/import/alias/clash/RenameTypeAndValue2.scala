import collection.mutable.Buffer
import collection.immutable.{LinearSeq => Buffer}

println(/* resolved: false */ LinearSeq.getClass)
println(classOf[/* resolved: false */ LinearSeq])

println(/* resolved: false */ Buffer.getClass)
println(classOf[/* resolved: false */ Buffer])