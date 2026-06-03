package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityWeigher
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}

class ScalaClassObjectWeigher extends ProximityWeigher {
  override def weigh(element: PsiElement, location: ProximityLocation): Comparable[_] = {
    val position = location.getPosition
    if (position == null || !position.getContainingFile.isInstanceOf[ScalaFile]) 0
    else
      Option(location.getProject) match {
        case Some(_) =>
          element match {
            case _: ScObject => 2
            case _: ScTypeDefinition => 4
            case _: ScTypeAlias => 3
            case _: PsiClass => 1
            case _ => 0
          }
        case _ => 0
      }
  }
}
