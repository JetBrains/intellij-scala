import actors.{Reaction, Future}

println(/* resolved: false */ Reaction.getClass)
println(/* resolved: false */ Future.getClass)
println(/* resolved: false */ AbstractActor.getClass)
println(classOf[/* path: scala.actors.Reaction */ Reaction])
println(classOf[/* path: scala.actors.Future */ Future])
println(classOf[/* resolved: false */ AbstractActor])
