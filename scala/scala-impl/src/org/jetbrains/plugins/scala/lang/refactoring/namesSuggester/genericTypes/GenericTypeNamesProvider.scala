package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType

/**
  * @author adkozlov
  */
trait GenericTypeNamesProvider {

  def names(`type`: ScParameterizedType): Seq[String]
}

object GenericTypeNamesProvider {

  val EP_NAME: ExtensionPointName[GenericTypeNamesProvider] =
    ExtensionPointName.create("org.intellij.scala.genericTypeNamesProvider")

  def providers: Seq[GenericTypeNamesProvider] = EP_NAME.getExtensions

  def isInheritor(`type`: ScParameterizedType, baseFqns: String*): Boolean =
    `type`.extractClass.exists { clazz =>
      val scope = ElementScope(clazz.getProject)

      baseFqns
        .flatMap(scope.getCachedClass)
        .exists(clazz.sameOrInheritor)
    }
}
