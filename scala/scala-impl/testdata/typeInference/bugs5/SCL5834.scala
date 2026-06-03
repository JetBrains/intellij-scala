object Colors extends Enumeration {
  type Colors = Value
  val foo, bar = Value
}

object Test extends App {
  def getEnumElementByIndex[T <: Enumeration](e: T, i: Int): T#Value = e(i)

  /*start*/getEnumElementByIndex(Colors, 1)/*end*/
}
//Colors.Value