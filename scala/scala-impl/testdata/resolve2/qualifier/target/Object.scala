object O1 {
  object O2
}

println(O1./* line: 2 */O2.getClass)
println(classOf[O1./* resolved: false */O2])