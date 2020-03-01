trait Info[A] {
  val value = ???
}

trait BaseBarObj[A] {
  implicit val info: Info[A] = ???
}

trait BaseBar
object BaseBar extends BaseBarObj[BaseBar]

trait Bar  extends BaseBar
object Bar extends BaseBarObj[Bar]

implicit def conv(a: Int)(implicit info: Info[Bar]): Info[Bar] = ???

0.<ref>value
