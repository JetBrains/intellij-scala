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
Seq(any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    double2Double,
    double2DoubleConflict,
    doubleWrapper,
    float2Float,
    float2FloatConflict,
    float2double,
    floatWrapper,
    i2s,
    int2Integer,
    int2IntegerConflict,
    int2double,
    int2float,
    int2long,
    intWrapper,
    long2Long,
    long2LongConflict,
    long2double,
    long2float,
    longWrapper),
Some(i2s)
*/