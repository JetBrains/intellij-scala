import collection.mutable.{Buffer => _, _}

println(/* resolved: false */ Buffer.getClass)
println(classOf[/* resolved: false */ Buffer])

println(/* path: scala.collection.mutable.IndexedSeq  */ IndexedSeq.getClass)
println(classOf[/* path: scala.collection.mutable.IndexedSeq  */ IndexedSeq])
