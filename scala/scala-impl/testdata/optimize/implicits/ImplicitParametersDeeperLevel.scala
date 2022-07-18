// Notification message: null
class ImplicitParametersDeeperLevel {

  class A
  class B

  object D {
    implicit val s: B = new B
  }

  object K {
    implicit def g(implicit s: B): A = new A
  }


  def foo()(implicit x: A) = 123


  import D._
  import K._

  foo()
}
/*
class ImplicitParametersDeeperLevel {

  class A
  class B

  object D {
    implicit val s: B = new B
  }

  object K {
    implicit def g(implicit s: B): A = new A
  }


  def foo()(implicit x: A) = 123


  import D._
  import K._

  foo()
}
 */