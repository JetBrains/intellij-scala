Array(1, 3) match {
  case Array(_, r) => /*start*/Some(r)/*end*/
  case _ => None
}
//Some[Int]