import collection.mutable.{Buffer => A}

println(/* resolved: false */ Buffer.getClass)
classOf[/* resolved: false */ Buffer]

println(/* name: Buffer, path: scala.collection.mutable.Buffer */ A.getClass)
classOf[/* name: Buffer, path: scala.collection.mutable.Buffer */ A]
