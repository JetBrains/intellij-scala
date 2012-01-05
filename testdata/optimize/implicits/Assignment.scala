import java.awt.Dimension
import scala.swing.Swing._

class Dim {
  def dim = new Dimension(0, 0)
  def dim_=(d: Dimension) { println(d) }
}

object Test extends App {
  val d = new Dim
  d.dim = (10, 20)
}
/*import java.awt.Dimension
import scala.swing.Swing._

class Dim {
  def dim = new Dimension(0, 0)
  def dim_=(d: Dimension) { println(d) }
}

object Test extends App {
  val d = new Dim
  d.dim = (10, 20)
}*/