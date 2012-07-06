package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.util.TextRange

/**
 * Pavel.Fatin, 18.05.2010
 */

sealed class Message
case class Info(element: String, message: String) extends Message
case class Warning(element: String, message: String) extends Message
case class Error(element: String, message: String) extends Message
case class ErrorWithRange(range: TextRange, message: String) extends Message