package object implicits {
  implicit def intToString(x: Int) = x.toString + x.toString
  implicit val implicitInt: Int = 0
}