import actors.{Reaction => Actor}
import actors.Actor

println(/* resolved: false */ Reaction.getClass)
println(classOf[/* resolved: false */ Reaction])

println(/* path: scala.actors.Actor, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */ Actor.getClass)
println(classOf[/* name: Reaction, path: scala.actors.Reaction, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass */ Actor])
