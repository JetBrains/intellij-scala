object Sample {
  def foo[T](x: T)(y: T) = x
  def main(args: Array[String]) {
    val tup = (1, 2)
    "stop here"
  }
  def test(tup: (Int,  Int)) = tup._1
  def test2(tup: Tuple2[Int,  Int]) = tup._2
}