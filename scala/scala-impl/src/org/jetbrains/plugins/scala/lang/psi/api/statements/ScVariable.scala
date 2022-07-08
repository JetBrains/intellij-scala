package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.kVAR
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

import javax.swing.Icon

trait ScVariable extends ScValueOrVariable {

  override protected def keywordElementType: IElementType = kVAR

  override protected def isSimilarMemberForNavigation(member: ScMember, isStrict: Boolean): Boolean = member match {
    case other: ScVariable => super.isSimilarMemberForNavigation(other, isStrict)
    case _ => false
  }

  // TODO unify with ScFunction and ScValue
  override protected final def baseIcon: Icon = {
    var parent = getParent
    while (parent != null) {
      parent match {
        case _: ScExtendsBlock => return if (isAbstract) Icons.ABSTRACT_FIELD_VAR else Icons.FIELD_VAR
        case (_: ScBlock | _: ScalaFile) => return Icons.VAR
        case _ => parent = parent.getParent
      }
    }
    null
  }

  override def isStable: Boolean = {
    //SCL-19477
    if (this.isInScala3File)
      typeElement.exists(_.singleton)
    else
      false
  }
}