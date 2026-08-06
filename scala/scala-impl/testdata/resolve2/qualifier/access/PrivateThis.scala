()
class O {
  val o: O = new O
  private def f1 {}
  private[this] def f2 {}
  protected[this] def f3 {}

  /* */f1
  /* */f2
  /* */f3
  o./* */f1
  o./* accessible: false */f2
  o./* accessible: false */f3
  this./* */f2
  this./* */f2
  this./* */f3
  
  class B extends O {
    this./* */f3
  }
}