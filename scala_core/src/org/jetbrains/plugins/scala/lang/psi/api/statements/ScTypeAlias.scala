package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiDocCommentOwner
import icons.Icons
import javax.swing.Icon
import psi.ScalaPsiElement
import toplevel.ScPolymorphicElement
import toplevel.typedef.{ScDocCommentOwner, ScMember}
import types.ScType

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:46:00
*/

trait ScTypeAlias extends ScPolymorphicElement with ScMember with ScAnnotationsHolder with ScDocCommentOwner {
  override def getIcon(flags: Int): Icon = Icons.TYPE_ALIAS
}