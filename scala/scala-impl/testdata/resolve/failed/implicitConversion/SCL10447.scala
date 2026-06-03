trait ToInt[T] {
  def toInt(arg: T): Int
}

case class Foo(value: Int)

object Foo {
  def unapply[T](arg: T)(implicit tc: ToInt[T]): Option[Foo] = {
    Some(Foo(tc.toInt(arg)))
  }
}

implicit val stringToInt = new ToInt[String] {
  def toInt(s: String): Int = s.toInt
}

"42" match {
  // Red code
  case Foo(foo) => foo.value <ref>* 2
  case _ => "Not matched"
}