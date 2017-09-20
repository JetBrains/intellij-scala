object Utils {

  implicit class FooPimps(f: Foo) {
    def implicitMethod(): Unit = println(s"implicit foo method")
  }

}

class Foo {

  import Utils._

  /*resolved: true*/implicitMethod()

  def implicitMethod(x: String): Unit = ()
}