package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

sealed abstract class ExtractPart
case class TextPart(string: String) extends ExtractPart
case class ExprPart(exprText: String) extends ExtractPart

object ExtractPart {
  def from(stringPart: format.StringPart): Option[ExtractPart] = stringPart match {
    case format.Text(text) => Some(TextPart(text))
    case format.UnboundExpression(expr) => Some(ExprPart(expr.getText))
    case injection: format.Injection => Some(ExprPart(injection.value))
    case _ => None
  }
}