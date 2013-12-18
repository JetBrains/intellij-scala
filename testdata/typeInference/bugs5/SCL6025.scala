object Props {
  trait A
  trait B
  class C extends A with B
  def apply(x: A) = 1
  protected def apply(x: B) = "text"
}


/*start*/Props(new Props.C)/*end*/
//Int