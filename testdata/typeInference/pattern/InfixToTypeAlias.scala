object A {
  case class x(x: Int, y: Int)
}
type x = A.x

val z: Any = null
z match {
  case a x b => /*start*/a/*end*/
  case _ => null
}
//Int