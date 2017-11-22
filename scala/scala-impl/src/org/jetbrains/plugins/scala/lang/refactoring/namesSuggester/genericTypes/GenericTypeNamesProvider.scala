package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester
package genericTypes

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass}
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

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

  def isInheritor(`type`: ScParameterizedType, baseFqns: String*): Boolean = {
    def isInheritor(clazz: PsiClass) = {
      implicit val project: Project = clazz.getProject
      val psiFacade = JavaPsiFacade.getInstance(project)
      val scope = GlobalSearchScope.allScope(project)

      baseFqns.flatMap(fqn => Option(psiFacade.findClass(fqn, scope)))
        .exists(baseClass => clazz.isInheritor(baseClass, true) || areClassesEquivalent(clazz, baseClass))
    }

    `type`.extractClass.exists(isInheritor)
  }
}
