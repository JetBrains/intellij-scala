class Foo

class Bar {
  def doSomething = 239
}

object Outer {
  object Provider {
    implicit def foo2Bar(f: Foo) = new Bar
  }
}

object Test {
  def main() {

    import Outer._
    import Provider._

    val foo = new Foo
    foo.<ref>doSomething
  }
}