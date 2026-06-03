object SCL1771 {
  trait MX[M[_]] {
    val mint: M[Int]
  }
  (null: MX[Option]).mint

  trait MA[M[_], A] {
    def ∗[B](f: A => M[B]): M[B]
  }
  val f: Int => Option[String] = {x => Some("")}
  /*start*/(null: MA[Option, Int]) ∗ f/*end*/
}
//Option[String]