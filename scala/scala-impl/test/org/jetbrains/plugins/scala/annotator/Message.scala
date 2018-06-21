package org.jetbrains.plugins.scala.annotator

/**
 * Pavel.Fatin, 18.05.2010
 */

sealed class Message
case class Info(element: String, message: String) extends Message
case class Warning(element: String, message: String) extends Message
case class Error(element: String, message: String) extends Message