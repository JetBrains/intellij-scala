object O {
  type A = CC
}

case class CC

println(O./* resolved: false */A.getClass)
println(classOf[O./* line: 2 */A])