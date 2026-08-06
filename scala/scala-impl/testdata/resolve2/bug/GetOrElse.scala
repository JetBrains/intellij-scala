class GetOrElse {
  val z : Option[Int]

  def a(x: Unit) = 45
  def a(x: Int) = 47
  /* */a(z.getOrElse(34))
}