object Holder {
  private val v = ""
}

import Holder./* resolved: false */v

println(/* resolved: false */ v)
println(classOf[/* resolved: false */ v])
