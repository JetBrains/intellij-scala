trait T[A] {
  def thiss = this
}
val l: T[Int] = new T[Int]().thiss
//True