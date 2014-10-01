class Zoo {
  def g: Any = 1
  def test = g match {
    case l: List[s] =>
      /*start*/l(0)/*end*/
  }
}
//s