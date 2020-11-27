package scala.tasty.inspector

import scala.quoted.Reflection

// TODO TASTy reflect / inspect: better API / implementation separation, dynamic loading, https://github.com/lampepfl/dotty-feature-requests/issues/98
trait TastyConsumer {
  def apply(reflect: Reflection)(tree: reflect.delegate.Tree): Unit
}
