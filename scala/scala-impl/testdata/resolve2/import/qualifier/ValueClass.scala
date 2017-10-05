class C {
  def f {}
}

val v = /* resolved: false */C

import v./* resolved: false */f

println(/* resolved: false */ f)