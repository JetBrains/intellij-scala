package p1 {
package p2 {
case class C
}
}

import p1.p2./* */C

trait T {
  println( /* file: PackageNested, offset: 37 */ C.getClass)
  println(classOf[ /* file: PackageNested, offset: 37 */ C])
}