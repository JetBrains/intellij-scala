class IntRange(val from: Long, val to: Long) extends scala.annotation.StaticAnnotation

class RequiresPermission(val anyOf: Array[Int]) extends scala.annotation.StaticAnnotation

class RequiresStrPermission(val strs: Array[String]) extends scala.annotation.StaticAnnotation

class WithDefaultValue(val value: Int = 42) extends scala.annotation.StaticAnnotation

class SuppressLint(val value: String*) extends scala.annotation.StaticAnnotation

object Annotations {
  @RequiresPermission(anyOf = Array(1, 2, 3))
  @IntRange(from = 10, to = 0)
  @WithDefaultValue
  @SuppressLint("Lorem")
  def foo(): Int = 5

  @IntRange(0, 100)
  @SuppressLint("Lorem", "Ipsum", "Dolor")
  def bar(): Unit = ()

  @RequiresPermission(anyOf = Array(1, 2, 3))
  def fooWithArrLiteral(): Int = 5

  @RequiresStrPermission(strs = Array("a", "b", "c"))
  def fooWithStrArrLiteral(): Int = 3
}