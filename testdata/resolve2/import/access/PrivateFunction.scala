object Holder {
  private def f = {}
}

import Holder./* accessible: false */f

println(/* accessible: false */ f)
println(classOf[/* resolved: false */ f])
