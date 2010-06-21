Array(1,2).foreach(/* */println)
object Main {
  def foo() : Int = 34
  def foo(x: Int): Int = x

  val x :Int => Int = /* line: 4 */foo
}