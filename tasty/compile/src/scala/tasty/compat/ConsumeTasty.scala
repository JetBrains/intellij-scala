package scala.tasty.compat

// TODO TASTy reflect / inspect: better API / implementation separation, dynamic loading, https://github.com/lampepfl/dotty-feature-requests/issues/98
trait ConsumeTasty {
  def apply(jar: Option[String], filePaths: List[String], tastyConsumer: TastyConsumer): Unit
}
