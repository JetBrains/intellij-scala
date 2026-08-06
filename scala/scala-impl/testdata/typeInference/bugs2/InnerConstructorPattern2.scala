object Test2 {
  case class H[T](x: T)
  case class O[T](x: T) extends H[T](x)
  val x: H[Int] = new H[Int](3)

  x match {
    case s@H(z) => {
      /*start*/s/*end*/
    }
  }
}
//Test2.H[Int]