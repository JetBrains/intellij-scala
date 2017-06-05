package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

/**
  * @author adkozlov
  */
abstract class GenericTypeNamesProvider {

  import NameSuggester.namesByType

  def names(`type`: ScType): Seq[String] =
    `type` match {
      case ParameterizedType(designator, arguments) if isValid(`type`) =>
        namesByType(designator) ++ names(designator, arguments)
      case _ => Seq.empty
    }

  def isValid(`type`: ScType): Boolean

  protected def names(designator: ScType, arguments: Seq[ScType]): Seq[String]

  protected final def argumentNames(argument: ScType): Seq[String] =
    namesByType(argument, shortVersion = false)
}

object GenericTypeNamesProvider {

  val EP_NAME: ExtensionPointName[GenericTypeNamesProvider] =
    ExtensionPointName.create("org.intellij.scala.genericTypeNamesProvider")

  def providers: Seq[GenericTypeNamesProvider] = EP_NAME.getExtensions

  def isInheritor(`type`: ScType, baseFqns: String*): Boolean = {
    def isInheritor(clazz: PsiClass) = {
      implicit val project = clazz.getProject
      val psiFacade = JavaPsiFacade.getInstance(project)
      val scope = GlobalSearchScope.allScope(project)

      baseFqns.flatMap(fqn => Option(psiFacade.findClass(fqn, scope)))
        .exists(baseClass => clazz.isInheritor(baseClass, true) || areClassesEquivalent(clazz, baseClass))
    }

    `type`.extractClass.exists(isInheritor)
  }
}
