object Holder {
   private class C
}

import Holder./* resolved: false */C

println(/* resolved: false */ C)
println(classOf[/* resolved: false */ C])
