object O {
  class C
}

var v = O

import /* resolved: false */ v.C

println(classOf[/* resolved: false */ C])