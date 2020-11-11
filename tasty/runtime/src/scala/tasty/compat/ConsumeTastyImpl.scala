package scala.tasty.compat

class ConsumeTastyImpl extends ConsumeTasty {
  override def apply(jar: Option[String], filePaths: List[String], tastyConsumer: TastyConsumer): Unit = {
    // TODO TASTy inspect: provide an option to reuse the compiler instance, https://github.com/lampepfl/dotty-feature-requests/issues/97
    val inspector = new TastyInspector {
      override protected def processCompilationUnit0(reflect: Reflection)(root: reflect.delegate.Tree): Unit = {
        tastyConsumer.apply(reflect)(root)
      }
    }
    inspector.inspect0(jar, filePaths)
  }
}
