object Sample {
  def foo(x: Int, y: Int = 1, z: Int)(h: Int = x + y, m: Int) = x + y + z + h + m
  def main(args: Array[String]) {
    "stop here"
  }
}