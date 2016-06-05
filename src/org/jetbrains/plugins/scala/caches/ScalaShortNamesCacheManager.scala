package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.light.LightScalaMethod
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.Predef._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alefas
 * Date: 09.02.12
 */

class ScalaShortNamesCacheManager(project: Project) extends ProjectComponent {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager")

  def getClassByFQName(name: String, scope: GlobalSearchScope): PsiClass = {
    if (DumbService.getInstance(project).isDumb) return null

    val cleanName = ScalaNamesUtil.cleanFqn(name)
    val classes =
      StubIndex.getElements[java.lang.Integer, PsiClass](ScalaIndexKeys.FQN_KEY, cleanName.hashCode, project,
        new ScalaSourceFilterScope(scope, project), classOf[PsiClass])
    val iterator = classes.iterator()
    while (iterator.hasNext) {
      val clazz = iterator.next()
      if (ScalaNamesUtil.equivalentFqn(name, clazz.qualifiedName)) {
        clazz.getContainingFile match {
          case file: ScalaFile =>
            if (!file.isScriptFile(withCaching = true)) return clazz
          case _ => return clazz
        }
      }
    }
    null
  }

  def getClassesByFQName(fqn: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    if (DumbService.getInstance(project).isDumb) return Seq.empty

    val cleanName = ScalaNamesUtil.cleanFqn(fqn)

    val classes =
      StubIndex.getElements[java.lang.Integer, PsiClass](ScalaIndexKeys.FQN_KEY, cleanName.hashCode, project,
        new ScalaSourceFilterScope(scope, project), classOf[PsiClass])
    val buffer: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]
    var psiClass: PsiClass = null
    var count: Int = 0
    val iterator = classes.iterator()
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

  def getAllScalaFieldNames: Seq[String] = {
    val res: ArrayBuffer[String] = new ArrayBuffer[String]
    val valNames = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.VALUE_NAME_KEY, project)
    val valIterator = valNames.iterator()
    while (valIterator.hasNext) {
      res += valIterator.next()
    }
    val varNames = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.VARIABLE_NAME_KEY, project)
    val varIterator = varNames.iterator()
    while (varIterator.hasNext) {
      res += varIterator.next()
    }
    val classParamNames = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY, project)
    val classParamIterator = classParamNames.iterator()
    while (classParamIterator.hasNext) {
      res += classParamIterator.next()
    }
    res.toSeq
  }

  def getScalaFieldsByName( name: String, scope: GlobalSearchScope): Seq[PsiMember] = {
    val cleanName = ScalaNamesUtil.cleanFqn(name)
    val values =
      StubIndex.getElements(ScalaIndexKeys.VALUE_NAME_KEY, cleanName, project,
      new ScalaSourceFilterScope(scope, project), classOf[ScValue])
    val list: ArrayBuffer[PsiMember] = new ArrayBuffer[PsiMember]
    var member: PsiMember = null
    var count: Int = 0
    val valuesIterator = values.iterator()
    while (valuesIterator.hasNext) {
      val value = valuesIterator.next()
      if (value.declaredNames.map(ScalaNamesUtil.cleanFqn).contains(cleanName)) {
        list += value
        member = value
        count += 1
      }
    }
    val variables =
      StubIndex.getElements(ScalaIndexKeys.VARIABLE_NAME_KEY, cleanName, project,
        new ScalaSourceFilterScope(scope, project), classOf[ScVariable])
    val variablesIterator = variables.iterator()
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
    list.toSeq
  }

  def getAllMethodNames: Seq[String] = {
    val classNames = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.METHOD_NAME_KEY, project)
    import scala.collection.JavaConversions._
    classNames.toSeq
  }

  def getMethodsByName(name: String, scope: GlobalSearchScope): Seq[PsiMethod] = {
    val cleanName = ScalaNamesUtil.cleanFqn(name)
    def scalaMethods: Seq[PsiMethod] = {
      val methods =
        StubIndex.getElements(ScalaIndexKeys.METHOD_NAME_KEY, cleanName, project,
          new ScalaSourceFilterScope(scope, project), classOf[ScFunction])
      val list: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
      var method: PsiMethod = null
      var count: Int = 0
      val methodsIterator = methods.iterator()
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
      list.toSeq
    }
    def javaMethods: Seq[PsiMethod] = {
      PsiShortNamesCache.getInstance(project).getMethodsByName(cleanName, scope).filter {
        case f: ScFunction => false
        case f: LightScalaMethod => false
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

  def getClassesByName(name: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    val plainClasses =
      StubIndex.getElements(ScalaIndexKeys.SHORT_NAME_KEY, name, project, scope, classOf[PsiClass])
    import scala.collection.JavaConversions._
    plainClasses.toSeq
  }

  def getPackageObjectByName(fqn: String, scope: GlobalSearchScope): ScTypeDefinition = {
    if (DumbService.getInstance(project).isDumb) return null

    val cleanName = ScalaNamesUtil.cleanFqn(fqn)
    val classes =
      StubIndex.getElements[java.lang.Integer, PsiClass](ScalaIndexKeys.PACKAGE_OBJECT_KEY,
        cleanName.hashCode, project, scope, classOf[PsiClass])
    val classesIterator = classes.iterator()
    while (classesIterator.hasNext) {
      val psiClass = classesIterator.next()
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

  def getImplicitObjectsByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScObject] = {
    val classes = StubIndex.getElements(ScalaIndexKeys.IMPLICIT_OBJECT_KEY, ScalaNamesUtil.cleanFqn(fqn), project,
      new ScalaSourceFilterScope(scope, project), classOf[ScObject])
    val res: ArrayBuffer[ScObject] = new ArrayBuffer[ScObject]
    val classesIterator = classes.iterator()
    while (classesIterator.hasNext) {
      res += classesIterator.next()
    }
    res.toSeq
  }

  def getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    val otherClassNames = getClassNames(psiPackage, scope)
    val result: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]()
    for (clazzName <- otherClassNames) {
      val qualName =
        if (psiPackage.getQualifiedName.isEmpty) clazzName
        else psiPackage.getQualifiedName + "." + clazzName
      val c = ScalaPsiManager.instance(project).getCachedClasses(scope, qualName)
      result ++= c
    }
    result.toArray
  }

  def getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): mutable.HashSet[String] = {
    ScalaPsiManager.instance(project).getScalaClassNames(psiPackage, scope)
  }

  def getAllClassNames: Seq[String] = {
    val classNames = StubIndex.getInstance.getAllKeys(ScalaIndexKeys.SHORT_NAME_KEY, project)
    import scala.collection.JavaConversions._
    classNames.toSeq
  }


  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {}

  def getComponentName: String = "ScalaShortNamesCacheManager"
}

object ScalaShortNamesCacheManager {
  def getInstance(project: Project): ScalaShortNamesCacheManager = {
    project.getComponent(classOf[ScalaShortNamesCacheManager])
  }
}
