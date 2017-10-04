object Holder {
   protected case class C
}

import Holder.C

println(/* accessible: false */ C)
println(classOf[/* accessible: false */ C])
