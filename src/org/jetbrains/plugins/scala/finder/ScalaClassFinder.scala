package org.jetbrains.plugins.scala
package finder

import caches.ScalaCachesManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi._
import lang.psi.impl.ScalaPsiManager
import java.lang.String
import java.util.Set
import lang.psi.api.toplevel.typedef.ScClass
import lang.psi.ScalaPsiUtil
import collection.mutable.ArrayBuffer

class ScalaClassFinder(project: Project) extends PsiElementFinder {
  def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = {
    val buffer = new ArrayBuffer[PsiClass]
    val classes = ScalaCachesManager.getInstance(project).getNamesCache.getClassesByFQName(qualifiedName, scope)
    for (clazz <- classes) {
      clazz match {
        case c: ScClass if c.isCase =>
          val base = ScalaPsiUtil.getBaseCompanionModule(c)
          if (base == None)
            c.fakeCompanionModule match {
              case Some(o) => buffer += o
              case None =>
            }
        case _ =>
      }
    }
    buffer.toArray
  }

  def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = {
    val clazz = ScalaCachesManager.getInstance(project).getNamesCache.getClassByFQName(qualifiedName, scope)
    clazz match {
      case c: ScClass if c.isCase =>
        val base = ScalaPsiUtil.getBaseCompanionModule(c)
        if (c != None) null
        else c.fakeCompanionModule.getOrElse(null)
      case _ => null
    }
  }

  override def findPackage(qName: String): PsiPackage =
    ScalaPsiManager.instance(project).syntheticPackage(qName)

  override def getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    val settings = ScalaPsiUtil.getSettings(psiPackage.getProject)
    if (settings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES)
      ScalaCachesManager.getInstance(project).getNamesCache.getClassNames(psiPackage, scope)
    else
      super.getClassNames(psiPackage, scope)
  }

  override def getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    val settings = ScalaPsiUtil.getSettings(psiPackage.getProject)
    if (settings.IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES) {
      val otherClassNames = getClassNames(psiPackage, scope)
      val result: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]()
      import scala.collection.JavaConversions._
      for (clazzName <- otherClassNames) {
        val qualName = psiPackage.getQualifiedName + "." + clazzName
        val c = ScalaPsiManager.instance(project).getCachedClasses(scope, qualName)
        result ++= c
      }
      result.toArray
    } else super.getClasses(psiPackage, scope)
  }
}