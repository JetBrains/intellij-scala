import actors.{Actor => _, _}

println(/* resolved: false */ Actor.getClass)
println(classOf[/* resolved: false */ Actor])

println(/* path: scala.actors.Futures  */ Futures.getClass)
println(classOf[/* path: scala.actors.ReplyReactor  */ ReplyReactor])
