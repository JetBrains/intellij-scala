case class CC {
  def f {}
}

val v = /* */CC

import v./* resolved: false */f

println(/* resolved: false */ f)