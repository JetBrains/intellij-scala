// Notification message: null
trait TypeClass[T]

class MyImplicitUser[T: TypeClass]

object SCL12609 {
  object Implicits {
    implicit object MyImplicityInt extends TypeClass[Int]
  }

  import Implicits._

  new MyImplicitUser[Int] {}
}
/*
trait TypeClass[T]

class MyImplicitUser[T: TypeClass]

object SCL12609 {
  object Implicits {
    implicit object MyImplicityInt extends TypeClass[Int]
  }

  import Implicits._

  new MyImplicitUser[Int] {}
}
*/