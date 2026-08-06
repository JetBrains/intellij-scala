package org.jetbrains.plugins.scala.lang.psi.api.statements
package params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

trait ScParameterType extends ScalaPsiElement {

  def typeElement: ScTypeElement

  def isRepeatedParameter: Boolean = {
    if (getLastChild == null || getLastChild.getNode == null) return false //todo: how it possible? EA: 16600
    getLastChild.getNode.getElementType match {
      case ScalaTokenTypes.tIDENTIFIER if getLastChild.textMatches("*") => true
      case _ => false
    }
  }

  def isCallByNameParameter: Boolean = {
    findChildrenByType(ScalaTokenTypes.tFUNTYPE).nonEmpty
  }
}