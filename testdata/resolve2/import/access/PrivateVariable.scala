object Holder {
  private val v = ""
}

import Holder./* accessible: false */v

println(/* accessible: false */ v)
println(classOf[/* resolved: false */ v])
