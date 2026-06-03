object Holder {
  private trait T
}

import Holder./* */T

println(/* resolved: false */ T)
println(classOf[/* accessible: false */ T])
