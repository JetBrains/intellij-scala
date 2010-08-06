package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import psi.ScalaPsiElement
import statements.params.ScArguments
import statements.ScFunction
import types.{ScSimpleTypeElement, ScTypeElement}
import com.intellij.psi.PsiMethod
import resolve.ScalaResolveResult
import psi.types.ScType
import expr.{ScNewTemplateDefinition, ScArgumentExprList}
import psi.types.result.TypeResult

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScConstructor extends ScalaPsiElement {
  def typeElement: ScTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def args = findChild(classOf[ScArgumentExprList])
  def arguments: Seq[ScArgumentExprList] =
    collection.immutable.Seq(findChildrenByClassScala(classOf[ScArgumentExprList]).toSeq: _*)

  def expectedType: Option[ScType]

  def newTemplate: Option[ScNewTemplateDefinition]

  def shapeType(i: Int): TypeResult[ScType]
}