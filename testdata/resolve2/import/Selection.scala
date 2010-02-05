import actors.{Actor, Future}

println(/* path: scala.actors.Actor */ Actor.getClass)
println(classOf[/* path: scala.actors.Actor */ Actor])

println(/* resolved: false */ Future.getClass)
println(classOf[/* path: scala.actors.Future */ Future])

println(/* resolved: false */ AbstractActor.getClass)
println(classOf[/* resolved: false */ AbstractActor])
