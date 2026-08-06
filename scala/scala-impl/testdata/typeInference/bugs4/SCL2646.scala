import java.lang.Long

object P {
  def baz() = {
    val c: Char = /*start*/0x03/*end*/
    println("c: " + c)
  }

  def main(args: Array[String]) {
    baz
  }
}
//Char