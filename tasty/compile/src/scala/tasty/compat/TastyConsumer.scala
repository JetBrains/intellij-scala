package scala.tasty.compat

trait TastyConsumer {
  def apply(reflect: Reflection)(tree: reflect.Tree): Unit
}
