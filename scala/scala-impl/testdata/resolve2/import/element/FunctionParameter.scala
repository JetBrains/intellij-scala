object O {
  def f(p: String) = {}
}

import O./* resolved: false */f./* resolved: false */p

println(/* resolved: false */ p.getClass)
println(classOf[/* resolved: false */ p])