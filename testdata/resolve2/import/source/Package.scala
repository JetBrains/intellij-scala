package p {
case class C
}

import p./* */C

trait T {
  println(/* file: this, offset: 23 */ C.getClass)
  println(classOf[ /* file: this, offset: 23 */ C])
}