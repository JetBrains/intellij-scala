class A
class B extends A {
  def foo() = 1
}
object Sample {
  def main(args: Array[String]) {
    val a: A = new B
    "stop here"
  }
}