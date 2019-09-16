class SimpleAnnotation extends scala.annotation.StaticAnnotation

@SimpleAnnotation
class SimpleClass {

  private val field, another: Int = 1

  @java.lang.Deprecated
  def foo(): AnyRef = int2Integer(field)

  def bar(param: Int) = s"abc$field$param"
}
