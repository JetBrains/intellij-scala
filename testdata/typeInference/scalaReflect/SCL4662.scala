object SCL4662 {
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{currentMirror => m}
import scala.reflect.runtime.universe

/**
 * Developed with pleasure
 * User: hamsterofdeath
 * Date: 04.09.12
 * Time: 21:39
 */
object SomeObject {
  def main(args: Array[String]) {
    case class Test(a: String, b: List[Int])
    val lookAtMe = m.reflect(Test)
    val value = universe.typeOf[Test]
    val memb = value.members
    val methods = memb.filter(_.isMethod).map(_.asMethod).toArray
    /*start*/methods.flatMap(e => try {
      Some(lookAtMe.reflectMethod(e))
    } catch {
      case e: Throwable =>
        e.printStackTrace(); None
    })/*end*/
  }
}
}
//Array[runtime.universe.MethodMirror]