class O[T](z: T) {
  def xavava: T = z
}


1 match {
  case z : O[Boolean] => {
    import z._
    /*start*/xavava/*end*/
  }
}
//Boolean