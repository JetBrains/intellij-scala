object O {
  case object CO
}

println(O./* line: 2 */CO.getClass)
println(classOf[O./* resolved: false */CO])