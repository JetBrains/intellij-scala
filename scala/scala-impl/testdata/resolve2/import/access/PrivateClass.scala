object Holder {
   private class C
}

import Holder./* */C

println(/* resolved: false */ C)
println(classOf[/* accessible: false */ C])
