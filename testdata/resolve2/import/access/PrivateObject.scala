object Holder {
  private object O
}

import Holder./* accessible: false */O

println(/* accessible: false */ O)
println(classOf[/* resolved: false */ O])
