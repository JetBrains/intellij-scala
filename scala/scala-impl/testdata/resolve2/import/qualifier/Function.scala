object O {
  class C
}

var f = O

import /* resolved: false */ f.C

println(classOf[/* resolved: false */ C])