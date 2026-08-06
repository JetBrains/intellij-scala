import scalaz._
import Scalaz._
object ScalazProblem {
  val x:Validation[String, Int] = 1.success
  val y:Validation[String, Int] = "wrong".fail

  /*start*/(x |@| y) {(a, b) => a + b}/*end*/
}
//Validation[String, Int]