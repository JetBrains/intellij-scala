trait A {
  def foo: Int = 34
}

class C {
  def foo: Int = 77
}
object Main {
  val g = new A with C {override def foo: Int = super.<ref>foo}
}