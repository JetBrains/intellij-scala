object O {
  class C
}

val v = O

import v.C

println(classOf[/* */ C])