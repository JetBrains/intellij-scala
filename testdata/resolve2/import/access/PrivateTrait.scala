object Holder {
  private trait T
}

import Holder./* accessible: false */T

println(/* resolved: false */ T)
println(classOf[/* accessible: false */ T])
