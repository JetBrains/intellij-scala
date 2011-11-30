object Holder {
  private val v = ""
}

import Holder./* */v

println(/* accessible: false */ v)
println(classOf[/* resolved: false */ v])
