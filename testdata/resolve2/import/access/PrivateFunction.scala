object Holder {
  private def f = {}
}

import Holder./* resolved: false */f

println(/* resolved: false */ f)
println(classOf[/* resolved: false */ f])
