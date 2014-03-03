class A
class B extends A {val x = 23}
object Sample {
  val x: A = new B
  def main(args: Array[String]) {
    "stop here"
  }
}