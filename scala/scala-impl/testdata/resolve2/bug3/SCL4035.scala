trait X {
  def x = println("x")
}

trait Y {
  self: X =>
  override def x = println("y")
}

(new X with Y)./* line: 7 */x