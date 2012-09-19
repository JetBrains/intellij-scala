object O extends Dynamic {
  def applyDynamic(s: String)(i: Int): Boolean = { true }
}
/*start*/O.foo/*end*/(1)
//(Int) => Boolean