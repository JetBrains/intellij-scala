trait SCL8398 {
  trait T
  object T {
    object Prod
    case class Prod(p: Product2[Long, String]) extends T
  }

  val t: T = ???
  t match {
    case T.Prod(p) => /*start*/p/*end*/
  }
}
//Product2[Long, String]