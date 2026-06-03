object SCL6660 {
  sealed trait JValue
  case class JInt(amount: BigInt) extends JValue {
    override def toString = "JInt(%s)" format amount
  }

  case class JDouble(amount: Double) extends JValue {
    override def toString = "JDouble(%s)" format amount
  }

  trait Implicits {
    implicit def int2jvalue(x: Int): JValue = JInt(x)
    implicit def double2jvalue(x: Double): JValue
  }

  object Problematic {
    trait DoubleConversion {
      implicit def double2jvalue(x: Double): JValue = JDouble(x)
    }

    object Conversions extends Implicits with DoubleConversion
    object DoubleConversion extends Implicits with DoubleConversion // Scala plugin 0.30.380 does not like this line, even though it's ultimately unused.
  }

  object Main extends App {
    def foo(x: JValue) = 123
    def foo(s: String) = ""

    {
      import Problematic.Conversions.{int2jvalue,double2jvalue}
      /*start*/foo(5)/*end*/
    }
  }
}
//Int