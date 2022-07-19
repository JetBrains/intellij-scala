// Notification message: null
import java.awt.Dimension

object T {
  implicit def tuple2Dimension(t: (Int, Int)) = new Dimension(t._1, t._2)
}

class Dim {
  def dim = new Dimension(0, 0)
  def dim_=(d: Dimension) { println(d) }
}

object Test extends App {
  import T._
  val d = new Dim
  d.dim = (10, 20)
}
/*import java.awt.Dimension

object T {
  implicit def tuple2Dimension(t: (Int, Int)) = new Dimension(t._1, t._2)
}

class Dim {
  def dim = new Dimension(0, 0)
  def dim_=(d: Dimension) { println(d) }
}

object Test extends App {
  import T._
  val d = new Dim
  d.dim = (10, 20)
}*/