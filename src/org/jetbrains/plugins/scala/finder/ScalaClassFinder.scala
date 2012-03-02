package org.jetbrains.plugins.scala
package finder

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi._
import java.lang.String
import caches.ScalaShortNamesCacheManager
import lang.psi.impl.ScalaPsiManager
import com.intellij.openapi.project.{DumbServiceImpl, Project}
import collection.mutable.ArrayBuffer
import stubs.StubIndex
import lang.psi.stubs.index.ScalaIndexKeys
import com.intellij.openapi.vfs.VirtualFile
import util.PsiUtilCore
import com.intellij.util.indexing.FileBasedIndex
import java.util.{Collections, Set}
import com.intellij.openapi.diagnostic.Logger
import lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTrait, ScClass, ScObject}

class ScalaClassFinder(project: Project) extends PsiElementFinder {
  def findClasses(qualifiedName: String, scope: GlobalSearchScope): Array[PsiClass] = {
    val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(qualifiedName, scope)
    val res = new ArrayBuffer[PsiClass]
    for (clazz <- classes) {
      clazz match {
        case o: ScObject =>
          o.fakeCompanionClass match {
            case Some(clazz) => res += clazz
            case _ =>
          }
        case _ =>
      }
    }
    if (qualifiedName.endsWith("$")) {
      val nameWithoutDollar = qualifiedName.substring(0, qualifiedName.length() - 1)
      val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(nameWithoutDollar, scope)
      for (clazz <- classes) {
        clazz match {
          case c: ScClass =>
            c.fakeCompanionModule match {
              case Some(o) => res += o
              case _ =>
            }
          case _ =>
        }
      }
    } else if (qualifiedName.endsWith("$class")) {
      val nameWithoutDollar = qualifiedName.substring(0, qualifiedName.length() - 6)
      val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(nameWithoutDollar, scope)
      for (clazz <- classes) {
        clazz match {
          case c: ScTrait =>
            res += c.fakeCompanionClass
          case _ =>
        }
      }
    }
    res.toArray
  }

  def findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass = {
    val classes = findClasses(qualifiedName, scope)
    if (classes.length > 0) classes(0)
    else null
  }

  override def findPackage(qName: String): PsiPackage = null

  override def getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    if (DumbServiceImpl.getInstance(project).isDumb) return Collections.emptySet()
    var qualifier: String = psiPackage.getQualifiedName
    val classes = StubIndex.getInstance.get(ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY, qualifier, project,
      new ScalaSourceFilterScope(scope, project))
    import java.util.HashSet
    var strings: HashSet[String] = new HashSet[String]
    val classesIterator = classes.iterator()
    while (classesIterator.hasNext) {
      val element = classesIterator.next()
      if (!(element.isInstanceOf[PsiClass])) {
        var faultyContainer: VirtualFile = PsiUtilCore.getVirtualFile(element)
        LOG.error("Wrong Psi in Psi list: " + faultyContainer)
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        return null
      }
      var clazz: PsiClass = element.asInstanceOf[PsiClass]
      strings add clazz.getName
      clazz match {
        case t: ScTemplateDefinition =>
          for (name <- t.additionalJavaNames) strings add name
        case _ =>
      }
    }
    strings
  }

  override def getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    val otherClassNames = getClassNames(psiPackage, scope)
    val result: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]()
    import scala.collection.JavaConversions._
    for (clazzName <- otherClassNames) {
      val qualName = psiPackage.getQualifiedName + "." + clazzName
      val c = ScalaPsiManager.instance(project).getCachedClasses(scope, qualName)
      result ++= c
    }
    result.toArray
  }

  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.finder.ScalaClassFinder")
}