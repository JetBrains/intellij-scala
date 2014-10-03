package object implicits {
  implicit def intToString(x: Int) = x.toString + x.toString
  implicit val implicitInt: Int = 0

  implicit class IntWrapper(i: Int) {
    def triple() = 3 * i
  }

  implicit class BooleanWrapper(val b: Boolean) extends AnyVal {
    def naoborot() = !b
  }
}