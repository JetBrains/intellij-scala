import dotty.tools.dotc.printing.Texts.Text

/**
 * Exists as a compatibility layer for calling methods of [[Text]] which have different signatures in
 * different versions of Scala.
 */
private object TextCompat:
  def mkString(text: Text, width: Int): String =
    mkStringPreScala383(text, width)
      .orElse(mkStringPostScala383(text, width))
      .getOrElse(sys.error("Could not call the method mkString on dotty.tools.dotc.printing.Texts.Text"))

  /**
   * The [[Text.mkString]] method prior to Scala 3.8.3 has two parameters:
   *   1. width: Int
   *   2. withLineNumbers: Boolean (we always provide false as the value)
   */
  private def mkStringPreScala383(text: Text, width: Int): Option[String] =
    try
      val method = classOf[Text].getDeclaredMethod("mkString", classOf[Int], classOf[Boolean])
      method.setAccessible(true)
      val result = method.invoke(text, width, false).asInstanceOf[String]
      Option(result)
    catch
      case _: Exception => None

  /**
   * The [[Text.mkString]] method in Scala 3.8.3 and later has only one parameter, `width: Int`.
   * @see [[https://github.com/scala/scala3/commit/51190450e3b360bb01ca3b9b02576750f1b8f50d Scala 3 compiler change]]
   *      for the change which introduced this.
   */
  private def mkStringPostScala383(text: Text, width: Int): Option[String] =
    try
      val method = classOf[Text].getDeclaredMethod("mkString", classOf[Int])
      method.setAccessible(true)
      val result = method.invoke(text, width).asInstanceOf[String]
      Option(result)
    catch
      case _: Exception => None
