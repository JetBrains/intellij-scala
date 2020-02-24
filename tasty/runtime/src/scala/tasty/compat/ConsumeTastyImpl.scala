package scala.tasty.compat

class ConsumeTastyImpl extends ConsumeTasty {
  override def apply(classpath: String, classes: List[String], tastyConsumer: TastyConsumer): Unit = {
    val inspector = new TastyInspector {
      override protected def processCompilationUnit0(reflect: Reflection)(tree: reflect.Tree): Unit = {
        tastyConsumer.apply(reflect)(tree)
      }
    }
    inspector.inspect0(classpath, classes)
  }
}
