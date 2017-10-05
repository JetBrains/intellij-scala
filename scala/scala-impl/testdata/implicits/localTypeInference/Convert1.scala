import java.util.ArrayList

object Convert1 {
  implicit def convert[T](p: ArrayList[T]) = new ArrayList[Int](3)
  implicit def convert[T](p: T) = new String

	def m1(p: ArrayList[Int]) {}
  def m1(p: Int) {}
	def m2(p: String) {}
  def m2(p: Boolean) {}

	def main(args: Array[String]) {
		m1(/*start*/new ArrayList[Boolean](2)/*end*/)
		m2(123)
	}
}
/*
Seq(convert,
    convert,
    any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    any2stringfmt),
Some(convert)
*/