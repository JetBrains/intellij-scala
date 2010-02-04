import actors.{Futures => Actor}
import actors.Actor

println(/* resolved: false */ Futures.getClass)
println(classOf[/* resolved: false */ Futures])

println(/* name: Futures, path: scala.actors.Futures, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject */ Actor.getClass)
println(classOf[/* path: scala.actors.Actor, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait */ Actor])