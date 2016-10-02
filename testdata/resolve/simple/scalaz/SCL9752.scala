import scalaz._
import Scalaz._

object SCL9752 {
  val l: List[Map[String, Int]] = List(Map("s" -> 4))
  val x = l.sequenceU
  x.<ref>map { case (a, b) => a } //can't resolve map

  val k: Map[String, List[Int]] = ??? //ok
  k.map { case (a, g) => a } //ok
}
