object O {
  val v = ""
}

import O.v

println(/* file: Value, offset: 17 */ v.getClass)
println(classOf[/* resolved: false  */ v])