object SCL3427 {
  def first = (x: Int) => x + 1
  def second = (y: Int) => y * 2
  def together = /*start*/first andThen second/*end*/

  def main(args: Array[String]) {
    require(6 == together(2))
  }
}
//Int => Int