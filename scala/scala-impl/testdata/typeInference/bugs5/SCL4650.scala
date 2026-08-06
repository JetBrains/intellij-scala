object SCL4650 {
  case class A(a : Int)

  trait Mixin

  val a = new A(2) with Mixin

  /*start*/a.copy(3)/*end*/
}
//SCL4650.A