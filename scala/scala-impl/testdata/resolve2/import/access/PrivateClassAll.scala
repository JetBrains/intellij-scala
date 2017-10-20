object Holder {
   private class C
}

import Holder._

println(/* resolved: false */ C)
println(classOf[/* accessible: false */ C])
