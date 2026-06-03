Vector(1, 2, 3) match {
  case x +: xs => /*start*/(x, xs)/*end*/
  case _ => 0
}
//(Int, Vector[Int])