package org.jetbrains.plugins.scala.codeInspection.scalastyle

import java.text.MessageFormat
import java.util.Properties

import org.jetbrains.plugins.scala.extensions.using

object Messages {

  private lazy val messages = {
    try {
      val props = new Properties()
      using(getClass.getResourceAsStream("/reference.conf")) {
        props.load
      }
      props
    } catch {
      case _: Throwable => new Properties()
    }
  }

  def format(key: String, args: List[String], customMessage: Option[String]): String = {
    try {
      val rawMessage =
        Option(messages.getProperty(s"$key.message"))
          .getOrElse(messages.getProperty(key))
      val message = rawMessage.substring(0, rawMessage.length - 1).substring(1)
      MessageFormat.format(message, args: _*)
    } catch {
      case _: Throwable => customMessage.getOrElse("")
    }
  }

}
