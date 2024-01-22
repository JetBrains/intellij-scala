package org.jetbrains.plugins.scala.finder

import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiElementFinder, PsiPackage}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants.{PackageObjectClassName, TopLevelDefinitionsClassNameSuffix, TraitImplementationClassSuffix_211}
import org.jetbrains.plugins.scala.util.TopLevelMembers

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._

class ScalaClassFinder(project: Project) extends PsiElementFinder {
  private def psiManager  : ScalaPsiManager             = ScalaPsiManager.instance(project)
  private def cacheManager: ScalaShortNamesCacheManager = ScalaShortNamesCacheManager.getInstance(project)

  override def findClasses(qualifiedName0: String, scope: GlobalSearchScope): Array[PsiClass] = {
    if (psiManager == null || psiManager.isInJavaPsiFacade) {
      return Array.empty
    }

    /**
     * Handle classes defined in package objects.
     * In JVM such classes will be defined as a static class of a class with name "package"
     * (see [[org.jetbrains.plugins.scala.util.ScalaBytecodeConstants.PackageObjectClassName]])
     * Even though such classes can't be referenced directly from Java code,
     * they can be used in the compiler during type inference
     * This can happen for example when method defined in Scala package object has return type of the class
     * from the package object and the method is used in Java code.
     */
    val packageObjectClassPackagePart = "." + PackageObjectClassName + "."
    val isFromPackageObject = qualifiedName0.contains(packageObjectClassPackagePart)
    val qualifiedName = if (isFromPackageObject)
      qualifiedName0.replace(packageObjectClassPackagePart, ".")
    else
      qualifiedName0

    val classesWoSuffix: String => Seq[PsiClass] = (suffix: String) => {
      if (qualifiedName.endsWith(suffix)) {
        cacheManager.getClassesByFQName(qualifiedName.stripSuffix(suffix), scope)
      } else {
        Nil
      }
    }

    // These classes are presumably for the cases
    // when `com.intellij.psi.impl.PsiElementFinderImpl` doesn't find the class using Java indexes
    val classes = cacheManager.getClassesByFQName(qualifiedName, scope)
    val x: Seq[Option[PsiClass]] = classes.collect {
      case o: ScObject if !o.isPackageObject =>
        o.fakeCompanionClass
      case clazz if isFromPackageObject =>
        Some(clazz)
      case e: ScEnum => Some(e)
    }

    val classesWithout$ = classesWoSuffix("$")
    val x$: Seq[Option[PsiClass]] = classesWithout$.collect {
      case c: ScTypeDefinition =>
        c.fakeCompanionModule
    }

    val classesWithout$class = classesWoSuffix(TraitImplementationClassSuffix_211)
    val x$class: Seq[Option[PsiClass]] = classesWithout$class.collect {
      case c: ScTrait =>
        Option(c.fakeCompanionClass)
    }

    val src$packageObject: Option[PsiClass] =
      if (qualifiedName.contains(TopLevelDefinitionsClassNameSuffix)) {
        val targetFile = TopLevelMembers.findFileWithTopLevelMembers(project, scope, qualifiedName, TopLevelDefinitionsClassNameSuffix)
        targetFile.flatMap(_.topLevelWrapperObject)
      } else None

    val result = (x ++ x$ ++ x$class :+ src$packageObject).flatten.toArray
    result
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
