// https://youtrack.jetbrains.net/issue/SCL-1655
sealed trait MA[M[_], A] {
  def ma1[M[_], A](a: M[A]): MA[M, A]
  /**
   * Renaming the type parameter avoids the SOE.
   */
  def ma2[N[_], A](a: N[A]): MA[N, A]

  def mmi: M[M[Int]]

  val a = (
          ma2[M, M[Int]](mmi),  // return type correctly inferred
          ma2(mmi),             // infers type MA[Nothing, M[Int]]
          ma1[M, M[Int]](mmi),  // return type correctly inferred
          ma1(mmi)              // infers type MA[Nothing, M[Int]]
          )
  /*start*/a/*end*/
}
//(MA[M, M[Int]], MA[M, M[Int]], MA[M, M[Int]], MA[M, M[Int]])