package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import psi.ScalaPsiElement
import psi.types.ScType
import expr.{ScNewTemplateDefinition, ScArgumentExprList}
import psi.types.result.TypeResult
import types.{ScTypeArgs, ScParameterizedTypeElement, ScTypeElement}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScConstructor extends ScalaPsiElement {
  def typeElement: ScTypeElement

  def typeArgList: Option[ScTypeArgs] = typeElement match {
    case x: ScParameterizedTypeElement => Some(x.typeArgList)
    case _ => None
  }

  def args = findChild(classOf[ScArgumentExprList])

  def arguments: Seq[ScArgumentExprList] =
    Seq(findChildrenByClassScala(classOf[ScArgumentExprList]): _*)

  def expectedType: Option[ScType]

  def newTemplate: Option[ScNewTemplateDefinition]

  def shapeType(i: Int): TypeResult[ScType]

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]]
  
  def multiType(i: Int): Seq[TypeResult[ScType]]

  def reference: Option[ScStableCodeReferenceElement]
}