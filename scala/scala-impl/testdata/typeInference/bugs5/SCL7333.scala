object Bug {

  private[Bug] case class Bug(foo: String)

  def create(s: String) = /*start*/Bug(s)/*end*/

}

//Bug