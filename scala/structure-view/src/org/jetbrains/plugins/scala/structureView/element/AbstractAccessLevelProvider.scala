package org.jetbrains.plugins.scala.structureView.element

import com.intellij.ide.structureView.impl.java.AccessLevelProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScPackaging}
import org.jetbrains.plugins.scala.structureView.element.AbstractAccessLevelProvider.UnknownAccessLevel

trait AbstractAccessLevelProvider extends AccessLevelProvider { self: Element =>
  def isPublic: Boolean = getAccessLevel == PsiUtil.ACCESS_LEVEL_PUBLIC

  override def getSubLevel: Int = 0

  override def getAccessLevel: Int = getAccessLevel(self.element)

  protected def getAccessLevel(element: PsiElement): Int = element match
    case modifierListOwner@ScModifierListOwner.accessModifier(modifier) =>
      if (modifier.isPrivate) {
        if (modifierListOwner.getContext.is[ScFile, ScPackaging])
          PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL
        else PsiUtil.ACCESS_LEVEL_PRIVATE
      }
      else if (modifier.isProtected) PsiUtil.ACCESS_LEVEL_PROTECTED
      else PsiUtil.ACCESS_LEVEL_PUBLIC
    case _: ScModifierListOwner => PsiUtil.ACCESS_LEVEL_PUBLIC
    case _ => UnknownAccessLevel
}

object AbstractAccessLevelProvider {
  val UnknownAccessLevel: Int = -1
}
