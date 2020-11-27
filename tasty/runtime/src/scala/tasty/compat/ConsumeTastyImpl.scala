package scala.tasty.compat

import scala.quoted.Reflection
import scala.tasty.inspector.{ConsumeTasty, TastyConsumer}

class ConsumeTastyImpl extends ConsumeTasty {
  override def apply(classpath: String, classes: List[String], tastyConsumer: TastyConsumer): Unit = {
    // TODO TASTy inspect: provide an option to reuse the compiler instance, https://github.com/lampepfl/dotty-feature-requests/issues/97
    val inspector = new TastyInspector {
      override protected def processCompilationUnit0(reflect: Reflection)(root: reflect.delegate.Tree): Unit = {
        tastyConsumer.apply(reflect)(root)
      }
    }
    inspector.inspect0(classpath, classes)
  }
}
