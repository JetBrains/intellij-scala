object Main {
  class A()
  case class RichA() {
    def apply(s: String): Int = 1
  }
  implicit def toRichA(a: A): RichA = null
  val a = new A()
  implicit def i2s(x: Int): String = ""
  a(/*start*/23/*end*/)
}
/*
Seq(ArrowAssoc,
    Ensuring,
    StringFormat,
    double2Double,
    doubleWrapper,
    float2Float,
    floatWrapper,
    i2s,
    int2Integer,
    int2double,
    int2float,
    int2long,
    intWrapper,
    long2Long,
    longWrapper,
    any2stringadd),
Some(i2s)
*/