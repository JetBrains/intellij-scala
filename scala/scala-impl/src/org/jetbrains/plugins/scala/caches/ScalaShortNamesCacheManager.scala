package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.PsiMethodWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys._
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ScClassFqnIndex, ScPackageObjectFqnIndex}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._

import scala.collection.immutable.ArraySeq.unsafeWrapArray
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Service(Array(Service.Level.PROJECT))
final class ScalaShortNamesCacheManager(implicit project: Project) {

  def getClassByFQName(fqn: String, scope: GlobalSearchScope): PsiClass = {
    if (DumbService.getInstance(project).isDumb) {
      return null
    }

    val elements = ScClassFqnIndex.instance.getElements(fqn, project, scope).asScala
    elements
      .find { cls =>
        val qualifiedName = cls.qualifiedName
        qualifiedName != null && equivalentFqn(fqn, qualifiedName)
      }
      .orNull
  }

  def getClassesByFQName(fqn: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    if (DumbService.getInstance(project).isDumb) {
      return Nil
    }

    ScClassFqnIndex.instance.getElements(fqn, project, scope).asScala
      .filter(cls => cls.qualifiedName != null && equivalentFqn(fqn, cls.qualifiedName))
      .flatMap {
        case cls: ScTypeDefinition => // Add fakeCompanionModule when ScTypeDefinition
          Seq(cls) ++ cls.fakeCompanionModule.toSeq
        case cls =>
          Seq(cls)
      }
      .toIndexedSeq
  }

  def methodsByName(name: String)
                   (implicit scope: GlobalSearchScope): Iterable[PsiMethod] = {
    val cleanName = cleanFqn(name)
    allFunctionsByName(cleanName) ++ allMethodsByName(cleanName, psiNamesCache)
  }

  def getClassesByName(name: String, scope: GlobalSearchScope): Iterable[PsiClass] =
    SHORT_NAME_KEY.elements(name, scope)

  def findPackageObjectByName(fqn: String, scope: GlobalSearchScope): Option[ScObject] = {
    if (DumbService.getInstance(project).isDumb) {
      None
    } else {
      ScPackageObjectFqnIndex.instance.getElements(fqn, project, scope).asScala
        .collectFirst {
          case scalaObject: ScObject if scalaObject.qualifiedName != null &&
            equivalentFqn(fqn, scalaObject.qualifiedName.stripSuffix(".`package`")) => scalaObject
        }
    }
  }

  def allProperties(predicate: String => Boolean)
                   (implicit scope: GlobalSearchScope): Iterable[ScValueOrVariable] =
    for {
      propertyName <- PROPERTY_NAME_KEY.allKeys ++ CLASS_PARAMETER_NAME_KEY.allKeys
      if predicate(propertyName)

      cleanName = cleanFqn(propertyName)

      property <- PROPERTY_NAME_KEY.elements(cleanName, scope)
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
    METHOD_NAME_KEY.elements(cleanName, scope).filter { function =>
      equivalentFqn(cleanName, function.name)
    }

  def allFields(predicate: String => Boolean)
               (implicit scope: GlobalSearchScope): Iterable[PsiField] = {
    val namesCache = psiNamesCache

    for {
      fieldName <- unsafeWrapArray(namesCache.getAllFieldNames)
      if predicate(fieldName)

      field <- namesCache.getFieldsByName(fieldName, scope)
    } yield field
  }

  def allMethods(predicate: String => Boolean)
                (implicit scope: GlobalSearchScope): Iterable[PsiMethod] = {
    val namesCache = psiNamesCache

    for {
      methodName <- unsafeWrapArray(namesCache.getAllMethodNames)
      if predicate(methodName)

      method <- allMethodsByName(cleanFqn(methodName), namesCache)
    } yield method
  }

  private def allMethodsByName(cleanName: String, namesCache: PsiShortNamesCache)
                              (implicit scope: GlobalSearchScope) =
    namesCache.getMethodsByName(cleanName, scope).filter {
      case _: ScFunction |
           _: PsiMethodWrapper[_] => false
      case _ => true
    }

  private def psiNamesCache = PsiShortNamesCache.getInstance(project)
}

object ScalaShortNamesCacheManager {

  def getInstance(implicit project: Project): ScalaShortNamesCacheManager =
    project.getService(classOf[ScalaShortNamesCacheManager])
}
