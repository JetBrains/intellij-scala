object Holder {
   private class C
}

import Holder./* accessible: false */C

println(/* resolved: false */ C)
println(classOf[/* accessible: false */ C])
