class TestAnnotation extends scala.annotation.StaticAnnotation

object Test {
  def foo() {
    @TestAnnotation
    val bar = "lorem ipsum"

    val a, b, c = 1

    @TestAnnotation
    val x, y, z = 2
  }
}