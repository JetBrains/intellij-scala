class HH[X](val x: X)
class PostfixTest {
  def foo[T](implicit x: HH[T]): T = x.x
}
val a = new PostfixTest
implicit val z = new HH[Int](1)
/*start*/a foo/*end*/
//Int