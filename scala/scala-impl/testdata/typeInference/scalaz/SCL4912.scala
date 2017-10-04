import scalaz.Scalaz._
import scalaz._

class U {
  val v: Validation[Int, String] = null
  /*start*/v.bimap(_.toString, _ + "text")/*end*/
}
//Validation[String, String]