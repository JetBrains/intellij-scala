trait T[A] {
  def thiss = this
}
object Wrapper {
  val l: T[Int] = new T[Int]().thiss
}
//True