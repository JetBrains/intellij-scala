class AAAA {
  def foo(x: Int): Boolean = false
}

class BAAA extends AAAA {
  override def foo(x: Int) = {
    x match {
      case 1 => false
      case 2 => /*start*/!foo(3)/*end*/
      case _ => super.foo(x)
    }
  }
}
//Boolean