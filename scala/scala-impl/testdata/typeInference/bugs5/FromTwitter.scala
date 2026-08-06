trait U {
  trait S[T]
  type RN[A] = S[S[A]]
}

package object z extends U

object obj extends scala.AnyRef {
  def apply[A](rn: z.RN[A]) : A = null.asInstanceOf[A]
  def apply(sp : Int) : Int = 2
}

class FromTwitter {
  import z._

  def foo(r: RN[String]) = /*start*/obj.apply(r)/*end*/
}
//String