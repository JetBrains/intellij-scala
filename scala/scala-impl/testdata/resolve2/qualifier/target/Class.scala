object O {
  class C
}

println(O./* resolved: false */C.getClass)
println(classOf[O./* line: 2 */C])