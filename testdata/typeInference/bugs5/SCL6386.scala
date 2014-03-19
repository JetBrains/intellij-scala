class SCL6386[L] {

  class Wrapper[A](w: A)

  trait Tupler[L] {
    type Out <: Product

    def apply(l: L): Out
  }

  def test(l: L)(tupler : Tupler[L]): Wrapper[tupler.Out] = new Wrapper(/*start*/tupler(l)/*end*/)
}
//tupler.type#Out