import implicits._
object Sample {
  def main(args: Array[String]) {
    val i1 = 123
    def bar(s: String)(implicit i: Int) = if (i < s.length) s.charAt(i) else '0'
    "stop"
  }
}