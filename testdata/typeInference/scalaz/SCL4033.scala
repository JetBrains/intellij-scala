import scalaz._
import Scalaz._
object ScalazProblem {
  val x:Validation[String, Int] = 1.success
  val y:Validation[String, Int] = "wrong".fail

  /*start*/(x |@| y) {(a, b) => a + b}/*end*/
}
//(Unapply[Apply, Validation[String, Int]] {type M[X] = Validation[String, X]; type A = Int})#M[Int]