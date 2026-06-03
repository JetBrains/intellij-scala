import collection.mutable.{Buffer => A, _}

println(/* resolved: false */ Buffer.getClass)
println(classOf[/* resolved: false */ Buffer])

println(/* name: Actor, path: scala.collection.mutable.Buffer  */ A.getClass)
println(classOf[/* name: Actor, path: scala.collection.mutable.Buffer  */ A])

println(/* path: scala.collection.mutable.IndexedSeq */ IndexedSeq.getClass)
println(classOf[/* path: scala.collection.mutable.IndexedSeq */ IndexedSeq])
