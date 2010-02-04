import actors.{Actor => A, _}

println(/* resolved: false */ Actor.getClass)
println(classOf[/* resolved: false */ Actor])

println(/* name: Actor, path: scala.actors.Actor  */ A.getClass)
println(classOf[/* name: Actor, path: scala.actors.Actor  */ A])

println(/* path: scala.actors.Futures */ Futures.getClass)
println(classOf[/* path: scala.actors.ReplyReactor */ ReplyReactor])
