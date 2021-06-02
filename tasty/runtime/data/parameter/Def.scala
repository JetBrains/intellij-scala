package parameter

trait Def {
  def typeParameter[A]: Unit

  def typeParameters[A, B]: Unit

  def valueParameter(x: Int): Unit

  def valueParameters(x: Int, y: Int): Unit

  def noClause: Unit

  def emptyClause(): Unit

  def multipleClauses(x: Int)(y: Int): Unit

  def typeAndValueParameters[A](x: Int): Unit
}