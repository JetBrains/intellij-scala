package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import expr.ScArgumentExprList
import psi.ScalaPsiElement
import statements.params.ScArguments
import statements.ScFunction
import types.{ScSimpleTypeElement, ScTypeElement}
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScConstructor extends ScalaPsiElement {
  def typeElement = findChildByClassScala(classOf[ScTypeElement])

  def args = findChild(classOf[ScArgumentExprList])
  def arguments : Seq[ScArgumentExprList] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScArgumentExprList]).toSeq: _*)
}