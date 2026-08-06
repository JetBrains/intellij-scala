// Notification message: null
trait Scratch[T] {
  case object T
  case object U
  case object V
  case object W
  case object X
  case object Y
  case class Z[A]

  object Implicits {
    implicit def TToInt(t: T.type): Int = 0
    implicit def UToInt(u: U.type): Int = 0
    implicit def VToInt(v: V.type): Int = 0
    implicit def WToInt(w: W.type): Int = 0
    implicit def XToInt(x: X.type): Int = 0
    implicit def YToInt(x: Y.type): Int = 0
    implicit val x = X
    implicit val y = Y
    implicit val zInt: Z[Int] = Z[Int]
    implicit val zString: Z[String] = Z[String]
  }
  import Implicits.{TToInt, UToInt, VToInt, WToInt}

  def useT(t: Int) = error("stub")
  def useU[X](x: X) = error("stub")
  def useV(s: String, v : Int = 0) = error("stub")
  def useW(s: String)(w: Int) = error("stub")
  def useX(implicit w: Int) = error("stub")
  def useXY(s: String)(implicit x: X.type, y: Y.type) = error("stub")

  useT(T)
  useU[Int](U)
  useV(v = V, s = "")
  useW("")(W)

  def testUseXY {
    import Implicits.{x, y}
    useXY("")
  }

  def useZ[A](a: A)(implicit za: Z[A]): A = a

  def testUseZInt {
    import Implicits.{zInt}
    useZ(1)
  }
  def testUseZString {
    import Implicits.{zString}
    useZ("") + 1
  }
}
/*trait Scratch[T] {
  case object T
  case object U
  case object V
  case object W
  case object X
  case object Y
  case class Z[A]

  object Implicits {
    implicit def TToInt(t: T.type): Int = 0
    implicit def UToInt(u: U.type): Int = 0
    implicit def VToInt(v: V.type): Int = 0
    implicit def WToInt(w: W.type): Int = 0
    implicit def XToInt(x: X.type): Int = 0
    implicit def YToInt(x: Y.type): Int = 0
    implicit val x = X
    implicit val y = Y
    implicit val zInt: Z[Int] = Z[Int]
    implicit val zString: Z[String] = Z[String]
  }
  import Implicits.{TToInt, UToInt, VToInt, WToInt}

  def useT(t: Int) = error("stub")
  def useU[X](x: X) = error("stub")
  def useV(s: String, v : Int = 0) = error("stub")
  def useW(s: String)(w: Int) = error("stub")
  def useX(implicit w: Int) = error("stub")
  def useXY(s: String)(implicit x: X.type, y: Y.type) = error("stub")

  useT(T)
  useU[Int](U)
  useV(v = V, s = "")
  useW("")(W)

  def testUseXY {
    import Implicits.{x, y}
    useXY("")
  }

  def useZ[A](a: A)(implicit za: Z[A]): A = a

  def testUseZInt {
    import Implicits.zInt
    useZ(1)
  }
  def testUseZString {
    import Implicits.zString
    useZ("") + 1
  }
}
*/