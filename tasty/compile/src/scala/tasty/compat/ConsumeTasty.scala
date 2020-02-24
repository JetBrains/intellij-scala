package scala.tasty.compat

trait ConsumeTasty {
  def apply(classpath: String, classes: List[String], tastyConsumer: TastyConsumer): Unit
}
