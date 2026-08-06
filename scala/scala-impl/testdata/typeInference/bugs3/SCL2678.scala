package my.model

object TY {
  class TY[T]
  implicit def strToTy[T](s: String): TY[T] = new TY[T]

  val x: TY[Int] = /*start*/"Text"/*end*/
}
//TY.TY[Int]