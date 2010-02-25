object Holder {
   protected case class C
}

import Holder./* accessible: false */C

println(/* accessible: false */ C)
println(classOf[/* accessible: false */ C])
