import scala.language.existentials
class SCL10037 {

  trait A

  trait B[a <: A]{
    def c:CWithA[a]
  }

  trait C[a <: A, _ <: B[a]]

  type BAny = B[_ <: A]
  type CWithA[a <: A] = C[a, _ <: B[a]]
  type CAny =  C[a, _ <: B[a]] forSome {type a <: A}

  def f(c:CAny): Int = 1
  def f(s: String): String = s
  val b:BAny= null
  /*start*/f(b.c)/*end*/
}
//Int