package intellijscalastyle
package codeInspection

import java.util.Properties
import java.text.MessageFormat

object Messages {

  private lazy val messages = {
    try {
      val props = new Properties()
      props.load(getClass.getResourceAsStream("/reference.conf"))
      props
    } catch {
      case _: Throwable => new Properties()
    }
  }

  def format(key: String, args: List[String], customMessage: Option[String]): String = {
    try {
      val rawMessage = messages.getProperty(s"$key.message")
      val message = rawMessage.substring(0, rawMessage.length - 1).substring(1)
      MessageFormat.format(message, args: _*)
    } catch {
      case _: Throwable => customMessage.getOrElse("")
    }
  }

}
