object Holder {
  private object O
}

import Holder./* resolved: false */O

println(/* resolved: false */ O)
println(classOf[/* resolved: false */ O])
