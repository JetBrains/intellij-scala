package object holder {
  class C
  object O
}

import /* */holder._

trait T {
  println(/* line: 3 */ O.getClass)
  println(classOf[ /* line: 2 */ C])
}