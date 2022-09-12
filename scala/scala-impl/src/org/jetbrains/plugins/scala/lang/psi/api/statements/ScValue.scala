package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.kVAL
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

import javax.swing.Icon

trait ScValue extends ScValueOrVariable {

  override protected def keywordElementType: IElementType = kVAL

  override protected def isSimilarMemberForNavigation(member: ScMember, isStrictCheck: Boolean): Boolean = member match {
    case other: ScValue => super.isSimilarMemberForNavigation(other, isStrictCheck)
    case _ => false
  }

  // TODO unify with ScFunction and ScVariable
  override protected final def baseIcon: Icon = {
    @scala.annotation.tailrec
    def basedOnParent(current: PsiElement): Icon = current match {
      case _: ScExtendsBlock         => if (isAbstract) Icons.ABSTRACT_FIELD_VAL else Icons.FIELD_VAL
      case _: ScBlock | _: ScalaFile => Icons.VAL
      case other if other != null    => basedOnParent(current.getParent)
      case _                         => Icons.VAL
    }
    basedOnParent(getParent)
  }
}