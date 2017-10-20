object O {
  import collection.mutable.HashSet
}

println(O./* resolved: false */HashSet.getClass)
println(classOf[O./* resolved: false */HashSet])