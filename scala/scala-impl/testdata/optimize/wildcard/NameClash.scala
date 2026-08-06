// Notification message: null
import java.awt.{BasicStroke, Color, Dimension, Graphics2D, Point}

class NameClash {
  def render(g: Graphics2D) {
    g.setColor(Color.red)
    g.setStroke(new BasicStroke(2.0f))
    val d = new Dimension(10, 20)
    val p = new Point(30, 50)
    val list = List(1, 2, 3) //java.awt.List would clash with scala.List
  }
}

/*
import java.awt.{BasicStroke, Color, Dimension, Graphics2D, Point}

class NameClash {
  def render(g: Graphics2D) {
    g.setColor(Color.red)
    g.setStroke(new BasicStroke(2.0f))
    val d = new Dimension(10, 20)
    val p = new Point(30, 50)
    val list = List(1, 2, 3) //java.awt.List would clash with scala.List
  }
}
*/