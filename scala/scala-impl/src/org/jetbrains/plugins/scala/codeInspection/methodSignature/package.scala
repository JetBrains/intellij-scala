package org.jetbrains.plugins.scala.codeInspection

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
  * @author Nikolay.Tropin
  */
package object methodSignature {

  private[methodSignature] def isScalaJSFacade(c: PsiClass): Boolean = {
    if (c == null) false
    else findJsAny(c.getProject).exists(c.isInheritor(_, /*checkDeep =*/true))
  }

  private def findJsAny(project: Project): Option[PsiClass] = {
    val manager = ScalaPsiManager.instance(project)
    val scope = GlobalSearchScope.allScope(project)
    manager.getCachedClasses(scope, "scala.scalajs.js.Any").find(_.isInstanceOf[ScTrait])
  }
}
