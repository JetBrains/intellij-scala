package org.jetbrains.plugins.scala
package finder

import java.util

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import scala.collection.mutable.ArrayBuffer

class ScalaClassFinder(project: Project) extends PsiElementFinder {
  private def manager: ScalaPsiManager = ScalaPsiManager.instance(project)

  def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = {
    if (manager.isInJavaPsiFacade) return Array.empty
    val res = new ArrayBuffer[PsiClass]

    def iterateClasses(suffix: String)(fun: PsiClass => Unit) {
      if (!qualifiedName.endsWith(suffix)) return
      val nameWithoutDollar = qualifiedName.substring(0, qualifiedName.length - suffix.length)
      val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(nameWithoutDollar, scope)
      classes.foreach(fun)
    }

    iterateClasses("") {
      case o: ScObject if !o.isPackageObject =>
        o.fakeCompanionClass match {
          case Some(c) => res += c
          case _ =>
        }
      case _ =>
    }

    iterateClasses("$") {
      case c: ScTypeDefinition =>
        c.fakeCompanionModule match {
          case Some(o) => res += o
          case _ =>
        }
      case _ =>
    }

    iterateClasses("$class") {
      case c: ScTrait => res += c.fakeCompanionClass
      case _ =>
    }

    res.toArray
  }

  def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = {
    val classes = findClasses(qualifiedName, scope)
    if (classes.length > 0) classes(0)
    else null
  }

  override def findPackage(qName: String): PsiPackage = null

  override def getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): util.Set[String] = {
    import scala.collection.JavaConverters._
    manager.getJavaPackageClassNames(psiPackage, scope).asJava
  }

  override def getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    if (manager.isInJavaPsiFacade) return Array.empty
    val otherClassNames = getClassNames(psiPackage, scope)
    val result: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]()
    import scala.collection.JavaConversions._
    for (clazzName <- otherClassNames) {
      val qualName = psiPackage.getQualifiedName + "." + clazzName
      result ++= manager.getCachedClasses(scope, qualName)
      result ++= findClasses(qualName, scope)
    }
    result.toArray
  }
}