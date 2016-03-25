
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

object Start {
  def main(args: Array[String]) {
    Foo.call(Start.<ref>func1)
  }

  def func1(a: A) = Future(a)
  def func1(a: A, b: Int) = Future(a)
}

object Foo {
  def call[A <: Letter, B](func: (A) => Future[A]): Unit = {
    // implementation
  }
}

sealed trait Letter {
  def name: String
}

class A extends Letter {
  override def name = "A"
}