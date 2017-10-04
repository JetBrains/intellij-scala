object Holder {
  private object O
}

import Holder./* */O

println(/* accessible: false */ O)
println(classOf[/* resolved: false */ O])
