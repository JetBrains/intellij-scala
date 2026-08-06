object Test {
  trait ME
  trait ES extends ME
  trait R

  sealed class M[-A, +B]
  type RM[T] = M[T, R]

  val r: RM[ME] = null
  /*caret*/
  val l: RM[ES] = r
}
//True