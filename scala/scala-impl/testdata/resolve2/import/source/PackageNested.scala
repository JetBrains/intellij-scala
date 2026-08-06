package p1 {
package p2 {
case class C
}
}

import p1.p2.C

trait T {
  println( /* */ C.getClass)
  println(classOf[ /* line: 3 */ C])
}