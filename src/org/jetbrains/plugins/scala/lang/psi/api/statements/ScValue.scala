package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import expr.ScBlock
import javax.swing.Icon
import toplevel.templates.ScExtendsBlock
import toplevel.ScTypedDefinition
import types.ScType
import toplevel.typedef._
import base.types.ScTypeElement
import expr.ScBlockStatement
import icons.Icons
import types.result.{TypeResult, TypingContext, Success}
import com.intellij.psi.PsiElement
import lexer.ScalaTokenTypes

/**
 * @author Alexander Podkhalyuzin
 */

trait ScValue extends ScBlockStatement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder with ScAnnotationsHolder {
  self =>
  def valKeyword = findChildrenByType(ScalaTokenTypes.kVAL).apply(0)

  def declaredElements: Seq[ScTypedDefinition]

  def declaredNames: Seq[String] = declaredElements.map(_.name)

  def hasExplicitType: Boolean = typeElement.isDefined

  def typeElement: Option[ScTypeElement]

  def declaredType: Option[ScType] = typeElement flatMap (_.getType(TypingContext.empty) match {
    case Success(t, _) => Some(t)
    case _ => None
  })

  def getType(ctx: TypingContext): TypeResult[ScType]


  override protected def isSimilarMemberForNavigation(m: ScMember, isStrict: Boolean): Boolean = m match {
    case other: ScValue =>
      for (elem <- self.declaredElements) {
        if (other.declaredElements.exists(_.name == elem.name))
          return true
      }
      false
    case _ => false
  }

  override def getIcon(flags: Int): Icon = {
    var parent = getParent
    while (parent != null) {
      parent match {
        case _: ScExtendsBlock => return Icons.FIELD_VAL
        case _: ScBlock => return Icons.VAL
        case _ => parent = parent.getParent
      }
    }
    null
  }

  def getValToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kVAL)

  override def isDeprecated = hasAnnotation("scala.deprecated") != None || hasAnnotation("java.lang.Deprecated") != None
}