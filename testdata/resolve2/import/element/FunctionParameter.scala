object O {
  def f(p: String) = {}
}

import O.f.p

println(/* resolved: false */ p.getClass)
println(classOf[/* resolved: false */ p])