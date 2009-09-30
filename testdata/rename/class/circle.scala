object OuterCircle {
  import java.awt.geom.Ellipse2D

  class Circle123(x: Double, y: Double) extends Ellipse2D.Double(x, y, 1, 2)


  object foo {
    val c: /*caret*/ Circle123 = null
  }
}
/*
object OuterCircle {
  import java.awt.geom.Ellipse2D

  class NameAfterRename(x: Double, y: Double) extends Ellipse2D.Double(x, y, 1, 2)


  object foo {
    val c: /*caret*/ NameAfterRename = null
  }
}
*/