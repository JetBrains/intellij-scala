package p {
case class C
}

import p.C

trait T {
  println(/* */ C.getClass)
  println(classOf[ /* line: 2 */ C])
}