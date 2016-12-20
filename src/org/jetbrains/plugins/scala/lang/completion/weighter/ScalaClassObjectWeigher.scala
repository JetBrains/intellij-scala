package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityWeigher
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.01.12
 */

class ScalaClassObjectWeigher extends ProximityWeigher {
  def weigh(element: PsiElement, location: ProximityLocation): Comparable[_] = {
    Option(location.getProject) match {
      case Some(prj) if !ScalaProjectSettings.getInstance(prj).isScalaPriority => null
      case Some(_) =>
        element match {
          case _: ScObject => 1
          case _: ScTypeDefinition => 3
          case _: ScTypeAlias => 2
          case _: PsiClass => 0
          case _ => null
        }
      case _ => null
    }
  }
}
