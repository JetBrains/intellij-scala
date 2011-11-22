package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import icons.Icons
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import com.intellij.psi._
import toplevel.{ScImportableDeclarationsOwner, ScModifierListOwner, ScTypedDefinition}
import types.result.{TypeResult, TypingContext}
import types.ScType
import util.PsiTreeUtil
import expr.{ScFunctionExpr, ScExpression}
import base.ScPrimaryConstructor

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScParameter extends ScTypedDefinition with ScModifierListOwner with
        PsiParameter with ScAnnotationsHolder with ScImportableDeclarationsOwner {
  def getTypeElement: PsiTypeElement

  def isWildcard: Boolean = "_" == name

  def typeElement: Option[ScTypeElement]

  def paramType: Option[ScParameterType]

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getIcon(flags: Int): Icon = Icons.PARAMETER

  def isRepeatedParameter: Boolean

  def isCallByNameParameter: Boolean

  def baseDefaultParam: Boolean

  def isDefaultParam: Boolean

  def getActualDefaultExpression: Option[ScExpression]

  def getDefaultExpression: Option[ScExpression]

  def getRealParameterType(ctx: TypingContext): TypeResult[ScType]

  def getSuperParameter: Option[ScParameter]

  def expectedParamType: Option[ScType]

  def owner: PsiElement = {
    ScalaPsiUtil.getContextOfType(this, true, classOf[ScFunctionExpr],
      classOf[ScFunction], classOf[ScPrimaryConstructor])
  }

  def remove()

  def isImplicitParameter: Boolean = {
    val clause = PsiTreeUtil.getParentOfType(this, classOf[ScParameterClause])
    if (clause == null) return false
    clause.isImplicit
  }
}