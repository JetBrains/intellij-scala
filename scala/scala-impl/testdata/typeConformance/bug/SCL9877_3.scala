object Test {
  trait ME
  trait ES extends ME
  trait R

  sealed class M[-A, +B]
  type EM[T] = M[T, ME]
  type RM[T] = M[T, R]

  type T = ME
  val r: EM[T] => RM[T] = null
  /*caret*/
  val l: EM[ME] => RM[ES] = r
}
//True