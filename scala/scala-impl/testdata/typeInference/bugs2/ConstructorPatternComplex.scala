case class H[T](x: T)
case class O[T](x: T) extends H[T](x)
val x: H[Int] = new H[Int](3)

x match {
  case s@O(z) => {
    /*start*/s/*end*/
  }
}
//O[Int]