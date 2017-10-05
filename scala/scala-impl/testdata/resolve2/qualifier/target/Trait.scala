object O {
  trait T
}

println(O./* resolved: false */T.getClass)
println(classOf[O./* line: 2 */T])