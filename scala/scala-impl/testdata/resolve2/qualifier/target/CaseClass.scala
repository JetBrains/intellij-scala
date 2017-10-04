object O {
  case class CC
}

println(O./* */CC.getClass)
println(classOf[O./* line: 2 */CC])