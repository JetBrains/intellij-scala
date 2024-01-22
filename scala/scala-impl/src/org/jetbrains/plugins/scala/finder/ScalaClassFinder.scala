package org.jetbrains.plugins.scala.finder

import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElementFinder, PsiPackage}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.util.TopLevelMembers

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._

class ScalaClassFinder(project: Project) extends PsiElementFinder {
  private def psiManager  : ScalaPsiManager             = ScalaPsiManager.instance(project)
  private def cacheManager: ScalaShortNamesCacheManager = ScalaShortNamesCacheManager.getInstance(project)

  override def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = {
    if (psiManager == null || psiManager.isInJavaPsiFacade) {
      return Array.empty
    }

    val classesWoSuffix: String => Seq[PsiClass] = (suffix: String) => {
      if (qualifiedName.endsWith(suffix)) {
        cacheManager.getClassesByFQName(qualifiedName.stripSuffix(suffix), scope)
      } else {
        Nil
      }
    }

    val x: Seq[Option[PsiClass]] = cacheManager.getClassesByFQName(qualifiedName, scope).collect {
      case o: ScObject if !o.isPackageObject =>
        o.fakeCompanionClass
      case e: ScEnum => Some(e)
    }

    val x$: Seq[Option[PsiClass]] = classesWoSuffix("$").collect {
      case c: ScTypeDefinition =>
        c.fakeCompanionModule
    }

    val x$class: Seq[Option[PsiClass]] = classesWoSuffix("$class").collect {
      case c: ScTrait =>
        Option(c.fakeCompanionClass)
    }

    val topLevelSourceSuffix = "$package"
    val src$packageObject: Option[PsiClass] =
      if (qualifiedName.contains(topLevelSourceSuffix)) {
        val targetFile = TopLevelMembers.findFileWithTopLevelMembers(project, scope, qualifiedName, topLevelSourceSuffix)
        targetFile.flatMap(_.topLevelWrapperObject)
      } else None

    (x ++ x$ ++ x$class :+ src$packageObject).flatten.toArray
  }

  override def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = {
    findClasses(qualifiedName, scope).headOption.orNull
  }

  override def findPackage(qName: String): PsiPackage = {
    if (psiManager == null || DumbService.isDumb(project)) {
      return null
    }
    psiManager.syntheticPackage(qName)
  }

  override def getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): util.Set[String] = {
    if (psiManager == null) Collections.emptySet()
    else psiManager.getJavaPackageClassNames(psiPackage, scope).asJava

  }

  override def getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    if (psiManager == null || psiManager.isInJavaPsiFacade) {
      return Array.empty
    }
    psiManager.getJavaPackageClassNames(psiPackage, scope)
      .flatMap { clsName =>
        val qualifiedName = psiPackage.getQualifiedName + "." + clsName
        psiManager.getCachedClasses(scope, qualifiedName) ++ findClasses(qualifiedName, scope)
      }
      .toArray
  }
}
