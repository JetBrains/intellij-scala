object Holder {
  private def f = {}
}

import Holder./* */f

println(/* accessible: false */ f)
println(classOf[/* resolved: false */ f])
