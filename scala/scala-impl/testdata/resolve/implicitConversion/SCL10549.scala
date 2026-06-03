object Sample extends App {
  case class Wrapper[T](value: T)

  implicit final class TestExtensions[C[X] <: Seq[X]](val v: Wrapper[C[String]]) extends AnyVal {
    def test = v.value.map(_ + "!")
  }

  Wrapper(Seq("a", "b", "c")).<ref>test
}