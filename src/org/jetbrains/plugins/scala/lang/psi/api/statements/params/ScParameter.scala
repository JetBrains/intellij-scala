package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import icons.Icons
import javax.swing.Icon
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import com.intellij.psi._
import api.statements.params._
import toplevel.{ScImportableDeclarationsOwner, ScModifierListOwner, ScNamedElement, ScTyped}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScParameter extends ScNamedElement with ScTyped with ScModifierListOwner with
        PsiParameter with ScAnnotationsHolder with ScImportableDeclarationsOwner {
  def getTypeElement: PsiTypeElement

  def isWildcard: Boolean = "_" == name

  def typeElement: Option[ScTypeElement]

  def paramType: Option[ScParameterType]

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getIcon(flags: Int): Icon = Icons.PARAMETER

  def isRepeatedParameter: Boolean = paramType match {
    case Some(p: ScParameterType) => p.isRepeatedParameter
    case None => false
  }

  override def isStable = findFirstChildByType(ScalaTokenTypes.kVAR) == null
}