object SCL3565 extends App {
  def maximalDistance(vel: Array[Int], con: Array[Int], fuel: Int) = {
    /*start*/(vel, con).zipped.map(_.toDouble / _).max * fuel/*end*/
  }
}
//Double