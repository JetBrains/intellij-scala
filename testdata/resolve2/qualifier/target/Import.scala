object O {
  import actors.Actor
}

println(O./* resolved: false */Actor.getClass)
println(classOf[O./* resolved: false */Actor])