package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import icons.Icons
import javax.swing.Icon
import psi.ScalaPsiElement
import toplevel.ScPolymorphicElement
import toplevel.typedef.{ScDocCommentOwner, ScMember}
import types.ScType
import base.types.ScExistentialClause
import com.intellij.psi.{PsiElement, PsiDocCommentOwner}
import lexer.ScalaTokenTypes

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:46:00
 */

trait ScTypeAlias extends ScPolymorphicElement with ScMember with ScAnnotationsHolder with ScDocCommentOwner {
  override def getIcon(flags: Int): Icon = Icons.TYPE_ALIAS

  override protected def isSimilarMemberForNavigation(m: ScMember, isStrict: Boolean) = m match {
    case t: ScTypeAlias => t.name == name
    case _ => false
  }

  def isExistentialTypeAlias: Boolean = {
    getContext match {
      case _: ScExistentialClause => true
      case _ => false
    }
  }

  override def isDeprecated =
    hasAnnotation("scala.deprecated") != None || hasAnnotation("java.lang.Deprecated") != None

  def getTypeToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kTYPE)
}