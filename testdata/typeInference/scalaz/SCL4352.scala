import scalaz._
import Scalaz._

class BindStopTest {

  val m1 = some(5)
  val m2 = some(4)
  val m3 = m1 *> m2

  val ri1 = (x: Int) => some(x)
  val ri2 = (x: Int) => some(x + 1)
  /*start*/for {
    j1 <- ri1
    j2 <- ri2
  } yield j1 *> j2/*end*/

}
//Int => Option[Int]