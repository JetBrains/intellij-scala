package parameter

trait Def {
  def typeParameter[A]: Unit

  def typeParameters[A, B]: Unit

  def valueParameter(x: Int): Unit

  def valueParameters(x: Int, y: Long): Unit

  def noClause: Unit

  def emptyClause(): Unit

  def multipleClauses(x: Int)(y: Long): Unit

  def multipleClausesEmpty1()(y: Long): Unit

  def multipleClausesEmpty2(x: Int)(): Unit

  def multipleClausesEmpty3()(): Unit

  def typeAndValueParameters[A](x: Int): Unit
}