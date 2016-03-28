import scala.util.Try

trait Example {
  type Value
  def stringToValue(s: String): Option[Value]
  def valueToString(v: Value): String

  object ExtractValue {
    def unapply(s: String): Option[Value] = Example.this.stringToValue(s)
  }
}

// An example implementation, just for a reference
class IntExample extends Example {
  override type Value = Int
  override def stringToValue(s: String): Option[Int] = Try(s.toInt).toOption
  override def valueToString(v: Int): String = v.toString
}

trait TestExample {
  val ex: Example

  def identity = "123" match {
    case ex.ExtractValue(n) => ex.valueToString(/*start*/n/*end*/)
  }
}
//ex.Value