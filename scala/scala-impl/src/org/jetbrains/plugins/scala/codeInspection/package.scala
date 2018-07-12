package org.jetbrains.plugins.scala

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

package object codeInspection {

  private[codeInspection] def conformsToTypeFromClass(scType: ScType, fqn: String)
                                                     (implicit projectContext: ProjectContext): Boolean =
    (scType != api.Null) && (scType != api.Nothing) && {
      ElementScope(projectContext)
        .getCachedClass(fqn)
        .map(createParameterizedType)
        .exists(scType.conforms)
    }

  private[this] def createParameterizedType(clazz: PsiClass) = {
    val designatorType = ScDesignatorType(clazz)
    clazz.getTypeParameters match {
      case Array() => designatorType
      case parameters => ScParameterizedType(designatorType, parameters.map(UndefinedType(_)))
    }
  }
}
