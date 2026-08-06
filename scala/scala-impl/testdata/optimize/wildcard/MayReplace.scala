// Notification message: Removed 5 imports, added 1 import
import java.awt.{BasicStroke, Color, Dimension, Graphics2D, Point}

class MayReplace {
  def render(g: Graphics2D) {
    g.setColor(Color.red)
    g.setStroke(new BasicStroke(2.0f))
    val d = new Dimension(10, 20)
    val p = new Point(30, 50)
  }
}

/*
import java.awt._

class MayReplace {
  def render(g: Graphics2D) {
    g.setColor(Color.red)
    g.setStroke(new BasicStroke(2.0f))
    val d = new Dimension(10, 20)
    val p = new Point(30, 50)
  }
}
*/