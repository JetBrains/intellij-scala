package object pair {
  class C
  object O
}

package pair {
}

import /* */pair._

trait T {
  println(/* line: 3 */ O.getClass)
  println(classOf[ /* line: 2 */ C])
}