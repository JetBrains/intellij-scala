package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.stubs.{StubIndex, StubIndexKey}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.light.PsiMethodWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.{JavaConverters, mutable}

/**
 * User: Alefas
 * Date: 09.02.12
 */

class ScalaShortNamesCacheManager(implicit project: Project) extends AbstractProjectComponent(project) {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager")

  import ScalaIndexKeys._

  def getClassByFQName(name: String, scope: GlobalSearchScope): PsiClass = {
    if (DumbService.getInstance(project).isDumb) return null

    val iterator = classesIterator(name, scope)
    while (iterator.hasNext) {
      val clazz = iterator.next()
      if (ScalaNamesUtil.equivalentFqn(name, clazz.qualifiedName)) {
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
      if (ScalaNamesUtil.equivalentFqn(fqn, clazz.qualifiedName)) {
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

  def getAllScalaFieldNames: Iterable[String] = {
    import ScalaIndexKeys._
    VALUE_NAME_KEY.allKeys ++ VALUE_NAME_KEY.allKeys ++ CLASS_PARAMETER_NAME_KEY.allKeys
  }

  def getScalaFieldsByName( name: String, scope: GlobalSearchScope): Seq[PsiMember] = {
    val cleanName = ScalaNamesUtil.cleanFqn(name)

    val list = mutable.ArrayBuffer.empty[PsiMember]
    var member: PsiMember = null
    var count: Int = 0
    val valuesIterator = elementsIterator(cleanName, scope, VALUE_NAME_KEY, classOf[ScValue])
    while (valuesIterator.hasNext) {
      val value = valuesIterator.next()
      if (value.declaredNames.map(ScalaNamesUtil.cleanFqn).contains(cleanName)) {
        list += value
        member = value
        count += 1
      }
    }
    val variablesIterator = elementsIterator(cleanName, scope, VARIABLE_NAME_KEY, classOf[ScVariable])
    while (variablesIterator.hasNext) {
      val variable = variablesIterator.next()
      if (variable.declaredNames.map(ScalaNamesUtil.cleanFqn).contains(cleanName)) {
        list += variable
        member = variable
        count += 1
      }
    }
    if (count == 0) return Seq.empty
    if (count == 1) return Seq(member)
    list
  }

  def getAllMethodNames: Seq[String] = {
    import JavaConverters._
    StubIndex.getInstance.getAllKeys(ScalaIndexKeys.METHOD_NAME_KEY, project).asScala.toSeq
  }

  def getMethodsByName(name: String, scope: GlobalSearchScope): Seq[PsiMethod] = {
    val cleanName = ScalaNamesUtil.cleanFqn(name)
    def scalaMethods: Seq[PsiMethod] = {
      val list = mutable.ArrayBuffer.empty[PsiMethod]
      var method: PsiMethod = null
      var count: Int = 0
      val methodsIterator = elementsIterator(cleanName, scope, METHOD_NAME_KEY, classOf[ScFunction])
      while (methodsIterator.hasNext) {
        val m = methodsIterator.next()
        if (ScalaNamesUtil.equivalentFqn(cleanName, m.name)) {
          list += m
          method = m
          count += 1
        }
      }
      if (count == 0) Seq.empty
      if (count == 1) Seq(method)
      list
    }
    def javaMethods: Seq[PsiMethod] = {
      PsiShortNamesCache.getInstance(project).getMethodsByName(cleanName, scope).filter {
        case _: ScFunction => false
        case _: PsiMethodWrapper => false
        case _ => true
      }.toSeq
    }
    scalaMethods ++ javaMethods
  }

  def getFieldsByName(name: String, scope: GlobalSearchScope): Array[PsiField] = {
    PsiShortNamesCache.getInstance(project).getFieldsByName(name, scope)
  }

  def getAllJavaMethodNames: Array[String] = {
    PsiShortNamesCache.getInstance(project).getAllMethodNames
  }

  def getAllFieldNames: Array[String] = {
    PsiShortNamesCache.getInstance(project).getAllFieldNames
  }

  def getClassesByName(name: String, scope: GlobalSearchScope): Iterable[PsiClass] =
    SHORT_NAME_KEY.elements(name, scope, classOf[PsiClass])(project)

  def getPackageObjectByName(fqn: String, scope: GlobalSearchScope): ScTypeDefinition = {
    if (DumbService.getInstance(project).isDumb) return null

    val iterator = classesIterator(fqn, scope, PACKAGE_OBJECT_KEY)
    while (iterator.hasNext) {
      val psiClass = iterator.next()
      var qualifiedName: String = psiClass.qualifiedName
      if (qualifiedName != null) {
        if (psiClass.name == "`package`") {
          val i: Int = qualifiedName.lastIndexOf('.')
          if (i < 0) {
            qualifiedName = ""
          }
          else {
            qualifiedName = qualifiedName.substring(0, i)
          }
        }
        if (ScalaNamesUtil.equivalentFqn(fqn, qualifiedName)) {
          psiClass match {
            case typeDefinition: ScTypeDefinition =>
              return typeDefinition
            case _ =>
          }
        }
      }
    }
    null
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

  private def psiManager = ScalaPsiManager.instance(project)

  override def getComponentName: String = "ScalaShortNamesCacheManager"

  private def classesIterator(name: String, scope: GlobalSearchScope,
                              indexKey: StubIndexKey[java.lang.Integer, PsiClass] = FQN_KEY) =
    indexKey.integerElements(name, scope, classOf[PsiClass]).iterator

  private def elementsIterator[Psi <: PsiElement](cleanName: String, scope: GlobalSearchScope,
                                                  indexKey: StubIndexKey[String, Psi],
                                                  requiredClass: Class[Psi]) =
    indexKey.elements(cleanName, scope, requiredClass).iterator
}

object ScalaShortNamesCacheManager {
  def getInstance(project: Project): ScalaShortNamesCacheManager = {
    project.getComponent(classOf[ScalaShortNamesCacheManager])
  }
}
