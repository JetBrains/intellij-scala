trait Box[A] {
  def a: A
}

object Box {
  implicit def ToBox[A](a: A): Box[A] = null
}

import Box._
/*start*/1.a/*end*/
//Int