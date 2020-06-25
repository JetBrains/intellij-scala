package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.SuperMethodsSearch
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

private object extensions {

  implicit class PsiMethodExt(private val target: PsiMethod) extends AnyVal {
    def superMethods: Iterator[PsiMethod] = new SuperMethodsIterator(target)
  }

  private class SuperMethodsIterator(method: PsiMethod) extends Iterator[PsiMethod] {
    private var current = getSuper(method)

    override def hasNext: Boolean = current.isDefined
    override def next(): PsiMethod = {
      val res = current.get
      current = getSuper(res)
      res
    }

    private def getSuper(m: PsiMethod): Option[PsiMethod] = {
      val res = m match {
        case scalaMethod: ScFunction => scalaMethod.superMethod
        case javaMethod              => Option(SuperMethodsSearch.search(javaMethod, null, true, false).findFirst).map(_.getMethod)
      }
      res.map(selectActualMethod)
    }

    // TODO: check for what is this? apply / unapply?
    private def selectActualMethod(method: PsiMethod): PsiMethod =
      method.getNavigationElement match {
        case m: PsiMethod => m
        case _            => method
      }
  }
}
