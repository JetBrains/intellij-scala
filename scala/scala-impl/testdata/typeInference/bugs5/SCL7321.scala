object SCL6514 {
  class A

  implicit def a2i(a: A): Int = 12
  implicit def a2i2(a: A)(implicit s: String): Int = 14

  val x: Int = /*start*/new A/*end*/
}
//Int