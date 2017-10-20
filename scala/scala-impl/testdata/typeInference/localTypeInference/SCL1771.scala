object SCL1771 {
  trait MA[M[_]] {
    val mint: M[Int]
  }
  (null: MA[Option]).mint

  trait MA[M[_], A] {
    def ∗[B](f: A => M[B]): M[B]
  }
  val f: Int => Option[String] = {x => Some("")}
  /*start*/(null: MA[Option, Int]) ∗ f/*end*/
}
//Option[String]