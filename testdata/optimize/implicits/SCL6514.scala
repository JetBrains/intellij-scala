object SCL6514 {
  class A
  class B
  implicit def a2b(a: A)(implicit l: Int): B = new B

  object K {
    implicit val i: Int = 42
  }

  import SCL6514.K._

  val b: B = new A
}
/*
object SCL6514 {
  class A
  class B
  implicit def a2b(a: A)(implicit l: Int): B = new B

  object K {
    implicit val i: Int = 42
  }

  import SCL6514.K._

  val b: B = new A
}
 */