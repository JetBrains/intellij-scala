object Holder {
  private trait T
}

import Holder./* resolved: false */T

println(/* resolved: false */ T)
println(classOf[/* resolved: false */ T])
