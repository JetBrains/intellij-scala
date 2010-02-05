package p {
case class C
}

import p.C

trait T {
  println( /* file: Package, offset: 23 */ C.getClass)
  println(classOf[ /* file: Package, offset: 23 */ C])
}