import actors.{Actor => A}

println(/* resolved: false */ Actor.getClass)
classOf[/* resolved: false */ Actor]

println(/* name: Actor, path: scala.actors.Actor */ A.getClass)
classOf[/* name: Actor, path: scala.actors.Actor */ A]
