object Holder {
  private object O
}

import Holder./* unresolved */O

println(/* resolved: false */ O)
println(classOf[/* resolved: false */ O])
