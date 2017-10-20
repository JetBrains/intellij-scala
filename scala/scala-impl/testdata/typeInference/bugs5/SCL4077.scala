import scala.collection.mutable.ArrayBuffer

object SCL4077 extends App {

  println(produce(Array(0,-1,5,-2,3)))

  def produce(arr: Array[Int]) = {
    val pos = new ArrayBuffer[Int]
    val other = new ArrayBuffer[Int]
    for (a <- arr) {
      if (a < 0)
        pos += a
      else
        other += a
    }
    pos ++= /*start*/other/*end*/
  }
}
//ArrayBuffer[Int]