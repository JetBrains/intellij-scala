package tests

sealed trait Foo

case class Bar() extends Foo
case object Baz extends Foo

object Example {
  def foo(foo: Foo) = foo match {
    case Bar() =>
    case Baz =>
  }<caret>
}
