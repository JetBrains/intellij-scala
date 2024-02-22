package org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.genericTypes

import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

trait GenericTypeNamesProvider {

  def names(`type`: ScParameterizedType): Seq[String]
}

object GenericTypeNamesProvider extends ExtensionPointDeclaration[GenericTypeNamesProvider](
  "org.intellij.scala.genericTypeNamesProvider"
) {

  def providers: Seq[GenericTypeNamesProvider] = implementations

  // TODO: extract this, due to it doesn't relate to this Extension point and only confuses
  def isInheritor(`type`: ScType, baseFqns: String*): Boolean =
    `type`.extractClass.exists { clazz =>
      val scope = ElementScope(clazz.getProject)

      baseFqns
        .flatMap(scope.getCachedClass)
        .exists(clazz.sameOrInheritor)
    }
}
