package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType

trait GenericTypeNamesProvider {

  def names(`type`: ScParameterizedType): collection.Seq[String]
}

object GenericTypeNamesProvider extends ExtensionPointDeclaration[GenericTypeNamesProvider](
  "org.intellij.scala.genericTypeNamesProvider"
) {

  def providers: collection.Seq[GenericTypeNamesProvider] = implementations

  // TODO: extract this, due to it doesn't relate to this Extension point and only confuses
  def isInheritor(`type`: ScParameterizedType, baseFqns: String*): Boolean =
    `type`.extractClass.exists { clazz =>
      val scope = ElementScope(clazz.getProject)

      baseFqns
        .flatMap(scope.getCachedClass)
        .exists(clazz.sameOrInheritor)
    }
}
