import collection.mutable.{Buffer, AbstractMap}

println(/* path: scala.collection.mutable.Buffer */ Buffer.getClass)
println(classOf[/* path: scala.collection.mutable.Buffer */ Buffer])

println(/* resolved: false */ AbstractMap.getClass)
println(classOf[/* path: scala.collection.mutable.AbstractMap, accessible: false*/ AbstractMap])

println(/* resolved: false */ AbstractActor.getClass)
println(classOf[/* resolved: false */ AbstractActor])
