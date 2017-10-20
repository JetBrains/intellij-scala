package actions {
sealed case class Column(s: String, tp: Column.Type) {
}

object Column {
  sealed trait Type {}
  object Type {
    case object Integer extends Type
  }
}
}

class Temp {
  def foo(c: actions.Column.Type) = 1
  def foo(c: Int) = false
  /*start*/foo(actions.Column.Type.Integer)/*end*/
}
//Int