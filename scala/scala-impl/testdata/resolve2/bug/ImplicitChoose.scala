import java.util.ArrayList

object ImplicitChoose {
  implicit def convert[T](p: ArrayList[T]) = new ArrayList[Int](3)
  implicit def convert[T](p: T) = new String

	def m1(p: ArrayList[Int]) {}
  def m1(p: Int) {}
	def m2(p: String) {}
  def m2(p: Boolean) {}

	def main(args: Array[String]) {
		/* */m1(new ArrayList[Boolean](2))
		/* */m2(123)
	}
}