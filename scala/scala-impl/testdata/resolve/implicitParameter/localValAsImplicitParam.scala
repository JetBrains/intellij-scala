
object A {
  def a(implicit a: Int) = 0
}

object Client {
  implicit val a: Int = null
  A.<ref>a
}