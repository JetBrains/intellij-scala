import actors.Actor
import actors.{Reaction => Actor}

println(/* resolved: false */ Reaction.getClass)
println(classOf[/* resolved: false */ Reaction])

println(/* path: scala.actors.Actor, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */ Actor.getClass)
println(classOf[/* path: scala.actors.Actor, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait */ Actor])