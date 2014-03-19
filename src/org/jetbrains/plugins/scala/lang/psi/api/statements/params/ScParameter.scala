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
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScPrimaryConstructor}
import expr.{ScArgumentExprList, ScFunctionExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.search.{LocalSearchScope, GlobalSearchScope}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

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

  def deprecatedName: Option[String]

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

  def index = getParent.asInstanceOf[ScParameterClause].parameters.indexOf(this)

  override def getName: String = {
    val res = super.getName
    if (JavaLexer.isKeyword(res, LanguageLevel.HIGHEST)) "_" + res
    else res
  }

  abstract override def getUseScope = {
    val specificScope = getDeclarationScope match {
      case null => GlobalSearchScope.EMPTY_SCOPE
      case expr: ScFunctionExpr => new LocalSearchScope(expr)
      case clazz: ScClass if clazz.isCase => clazz.getUseScope
      case clazz: ScClass if this.isInstanceOf[ScClassParameter] => clazz.getUseScope //for named parameters
      case d => d.getUseScope
    }
    specificScope.intersectWith(super.getUseScope)
  }
}