package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.light.PsiMethodWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.mutable

/**
  * User: Alefas
  * Date: 09.02.12
  */
final class ScalaShortNamesCacheManager(implicit project: Project) extends ProjectComponent {

  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager")

  import ScalaIndexKeys._
  import ScalaNamesUtil._

  def getClassByFQName(name: String, scope: GlobalSearchScope): PsiClass = {
    if (DumbService.getInstance(project).isDumb) return null

    val iterator = classesIterator(name, scope)
    while (iterator.hasNext) {
      val clazz = iterator.next()
      if (equivalentFqn(name, clazz.qualifiedName)) {
        clazz.getContainingFile match {
          case file: ScalaFile =>
            if (!file.isScriptFile) return clazz
          case _ => return clazz
        }
      }
    }
    null
  }

  def getClassesByFQName(fqn: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    if (DumbService.getInstance(project).isDumb) return Seq.empty

    val buffer = mutable.ArrayBuffer.empty[PsiClass]
    var psiClass: PsiClass = null
    var count: Int = 0
    val iterator = classesIterator(fqn, scope)
    while (iterator.hasNext) {
      val clazz = iterator.next()
      if (equivalentFqn(fqn, clazz.qualifiedName)) {
        buffer += clazz
        count += 1
        psiClass = clazz
        clazz match {
          case s: ScTypeDefinition =>
            s.fakeCompanionModule match {
              case Some(o) =>
                buffer += o
                count += 1
              case _ =>
            }
          case _ =>
        }
      }
    }
    if (count == 0) return Seq.empty
    if (count == 1) return Seq(psiClass)
    buffer
  }

  def methodsByName(name: String)
                   (implicit scope: GlobalSearchScope): Iterable[PsiMethod] = {
    val cleanName = cleanFqn(name)
    allFunctionsByName(cleanName) ++ allMethodsByName(cleanName, psiNamesCache)
  }

  def getClassesByName(name: String, scope: GlobalSearchScope): Iterable[PsiClass] =
    SHORT_NAME_KEY.elements(name, scope, classOf[PsiClass])

  def findPackageObjectByName(fqn: String, scope: GlobalSearchScope): Option[ScObject] =
    if (DumbService.getInstance(project).isDumb) None
    else packageObjectByName(classesIterator(fqn, scope, PACKAGE_OBJECT_KEY), fqn)

  private def packageObjectByName(iterator: Iterator[PsiClass],
                                  fqn: String): Option[ScObject] = {
    while (iterator.hasNext) {
      val psiClass = iterator.next()
      psiClass.qualifiedName match {
        case null =>
        case qualifiedName =>
          val newQualifiedName = if (psiClass.name == "`package`") {
            qualifiedName.lastIndexOf('.') match {
              case -1 => ""
              case i => qualifiedName.substring(0, i)
            }
          } else qualifiedName

          psiClass match {
            case scalaObject: ScObject if equivalentFqn(fqn, newQualifiedName) =>
              return Some(scalaObject)
            case _ =>
          }
      }
    }

    None
  }

  def getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    val packageName = psiPackage.getQualifiedName match {
      case "" => ""
      case qualifiedName => s"$qualifiedName."
    }

    getClassNames(psiPackage, scope).toArray.map { className =>
      packageName + className
    }.flatMap {
      psiManager.getCachedClasses(scope, _)
    }
  }

  def getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] =
    psiManager.getScalaClassNames(psiPackage, scope)

  def allProperties(predicate: String => Boolean)
                   (implicit scope: GlobalSearchScope): Iterable[ScValueOrVariable] =
    for {
      propertyName <- PROPERTY_NAME_KEY.allKeys ++ CLASS_PARAMETER_NAME_KEY.allKeys
      if predicate(propertyName)

      cleanName = cleanFqn(propertyName)

      property <- PROPERTY_NAME_KEY.elements(cleanName, scope, classOf[ScValueOrVariable])
      if property.declaredNames.map(cleanFqn).contains(cleanName)
    } yield property

  def allFunctions(predicate: String => Boolean)
                  (implicit scope: GlobalSearchScope): Iterable[ScFunction] =
    for {
      functionName <- METHOD_NAME_KEY.allKeys
      if predicate(functionName)

      function <- allFunctionsByName(cleanFqn(functionName))
    } yield function

  private def allFunctionsByName(cleanName: String)
                                (implicit scope: GlobalSearchScope): Iterable[ScFunction] =
    METHOD_NAME_KEY.elements(cleanName, scope, classOf[ScFunction]).filter { function =>
      equivalentFqn(cleanName, function.name)
    }

  def allFields(predicate: String => Boolean)
               (implicit scope: GlobalSearchScope): Iterable[PsiField] = {
    val namesCache = psiNamesCache

    for {
      fieldName <- namesCache.getAllFieldNames
      if predicate(fieldName)

      field <- namesCache.getFieldsByName(fieldName, scope)
    } yield field
  }

  def allMethods(predicate: String => Boolean)
                (implicit scope: GlobalSearchScope): Iterable[PsiMethod] = {
    val namesCache = psiNamesCache

    for {
      methodName <- namesCache.getAllMethodNames
      if predicate(methodName)

      method <- allMethodsByName(cleanFqn(methodName), namesCache)
    } yield method
  }

  private def allMethodsByName(cleanName: String, namesCache: PsiShortNamesCache)
                              (implicit scope: GlobalSearchScope) =
    namesCache.getMethodsByName(cleanName, scope).filter {
      case _: ScFunction |
           _: PsiMethodWrapper => false
      case _ => true
    }

  private def psiManager = ScalaPsiManager.instance(project)

  private def psiNamesCache = PsiShortNamesCache.getInstance(project)

  override def getComponentName: String = "ScalaShortNamesCacheManager"

  private def classesIterator(name: String, scope: GlobalSearchScope,
                              indexKey: StubIndexKey[java.lang.Integer, PsiClass] = FQN_KEY) =
    indexKey.integerElements(name, scope, classOf[PsiClass]).iterator
}

object ScalaShortNamesCacheManager {

  def getInstance(implicit project: Project): ScalaShortNamesCacheManager =
    project.getComponent(classOf[ScalaShortNamesCacheManager])
}
