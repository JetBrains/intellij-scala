object SCL4416 {
trait Key

trait Mey

case class XKey(x: String, y: String = "text") extends Key with Mey

object StructuralTyping {
  type HasX = {def x: String}

  type HasY = {def y: String}

  def doX[T <: Key with ({def x: String}) with Mey with HasY](p: T): String = {
    /*start*/(p.x, p.y)/*end*/
    p.y
  }
}
}
//(String, String)