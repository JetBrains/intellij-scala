object SCL11119 {


  object test {
    case class F[T](v: T) {
      def onS[U](pf: PartialFunction[T,U]) : Unit = ???
    }
    implicit class F2[T](val f: F[T]) extends AnyVal {
      final def onS2[U]  = f.onS( _: PartialFunction[T,U])
    }
  }

  class test {
    import test._

    val f = F("foo")
    f./* applicable: true, name: apply */onS2{ case "x" => "baz" }
  }
}