object SCL5472 {
  class AA[A, B]
  class BB[A, B]

  def foo[A, B, C](x: A)(implicit a: AA[A, B], b: BB[B, C]): C = sys.exit()

  implicit def a[T]: AA[T, List[T]] = new AA
  implicit def b[T]: BB[T, List[T]] = new BB

  /*start*/foo("")/*end*/
}
//List[List[String]]