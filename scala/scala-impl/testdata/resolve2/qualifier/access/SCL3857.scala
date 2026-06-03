trait A extends B
trait B {
  private def foo = 1
  val a = new A {}
  a./* line: 3 */foo
}