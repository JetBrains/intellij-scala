package org.jetbrains.plugins.scala.hierarchy

import com.intellij.ide.hierarchy.`type`.JavaTypeHierarchyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum

final class ScalaTypeHierarchyProvider extends JavaTypeHierarchyProvider {
  override def getTarget(dataContext: DataContext): PsiClass = {
    val superTarget = super.getTarget(dataContext)
    superTarget match {
      case enum: ScEnum         => enum.syntheticClass.getOrElse(enum)
//      case enumCase: ScEnumCase =>
//        enumCase.getSyntheticCounterpart match {
//          case _ =>
//        }
      case other                => other
    }
  }
}
