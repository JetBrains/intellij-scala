import collection.generic.CanBuildFrom
import collection.IterableLike

object TestCase {
  def main(args: Array[String]) {
    val result = demonstrate(List("FOO"))
    println(/*start*/result.head/*end*/)
  }

  def demonstrate[Coll, That](stuff: Coll)(implicit ev: Coll <:< IterableLike[String, Coll],
                                           cbf1: CanBuildFrom[Coll, String, That]): That = {
    stuff map (_.toLowerCase)
  }
}
//String