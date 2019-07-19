object Main extends App {
  case class Foo(i: Int)

  def main(): Unit = {
    val foo = 42

    /*start*/
    val g = 123
    def aux(): Unit = {
      val bar = Foo(i = foo)
    }
    /*end*/
  }
}
/*
object Main extends App {
  case class Foo(i: Int)

  def main(): Unit = {
    val foo = 42


    testMethodName(foo)

  }

  def testMethodName(foo: Int): Unit = {
    val g = 123

    def aux(): Unit = {
      val bar = Foo(i = foo)
    }
  }
}
*/