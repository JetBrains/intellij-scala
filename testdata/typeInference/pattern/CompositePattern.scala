object CompositePattern {
  class C
  class A extends C
  class B extends C
  def main(args: Array[String]) {
    val x: C = new C
    x match {
      case t@(_: A | _: B) => {
        /*start*/t/*end*/
      }
      case _ =>
    }
  }
}
//C