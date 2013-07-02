package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.components.ProjectComponent
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import collection.mutable.ArrayBuffer
import com.intellij.psi._
import search.{PsiShortNamesCache, GlobalSearchScope}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import scala.Predef._
import org.jetbrains.plugins.scala.extensions.{toPsiNamedElementExt, toPsiClassExt}
import com.intellij.openapi.project.{DumbService, DumbServiceImpl, Project}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable, ScValue}
import org.jetbrains.plugins.scala.lang.psi.light.LightScalaMethod
import java.util
import collection.mutable
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil

/**
 * User: Alefas
 * Date: 09.02.12
 */

class ScalaShortNamesCacheManager(project: Project) extends ProjectComponent {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager")

  def getClassByFQName(name: String, scope: GlobalSearchScope): PsiClass = {
    if (DumbServiceImpl.getInstance(project).isDumb) return null

    val classes: util.Collection[_ <: PsiElement] =
      StubIndex.getInstance.get[java.lang.Integer, PsiClass](ScalaIndexKeys.FQN_KEY, name.hashCode, project,
        new ScalaSourceFilterScope(scope, project))
    val iterator = classes.iterator()
    while (iterator.hasNext) {
      val element = iterator.next()
      if (!ScalaStubsUtil.checkPsiForClass(element)) return null
      val clazz: PsiClass = element.asInstanceOf[PsiClass]
      if (name == clazz.getQualifiedName) {
        clazz.getContainingFile match {
          case file: ScalaFile =>
            if (!file.isScriptFile(withCashing = true)) return clazz
          case _ => return clazz
        }
      }
    }
    null
  }

  def getClassesByFQName(fqn: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    if (DumbServiceImpl.getInstance(project).isDumb) return Seq.empty

    val classes: util.Collection[_ <: PsiElement] =
      StubIndex.getInstance.get[java.lang.Integer, PsiClass](ScalaIndexKeys.FQN_KEY, fqn.hashCode, project,
        new ScalaSourceFilterScope(scope, project))
    val buffer: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]
    var psiClass: PsiClass = null
    var count: Int = 0
    val iterator = classes.iterator()
    while (iterator.hasNext) {
      val element: PsiElement = iterator.next()
      if (!element.isInstanceOf[PsiClass]) {
        val faultyContainer: VirtualFile = PsiUtilCore.getVirtualFile(element)
        LOG.error(s"Non class in class list: $faultyContainer. found: $element")
        if (faultyContainer != null && faultyContainer.isValid) {
          FileBasedIndex.getInstance.requestReindex(faultyContainer)
        }
        return Seq.empty
      }
      psiClass = element.asInstanceOf[PsiClass]
      if (fqn == psiClass.qualifiedName) {
        buffer += psiClass
        count += 1
      }
      psiClass match {
        case s: ScClass =>
          s.fakeCompanionModule match {
            case Some(o) =>
              buffer += o
              count += 1
            case _ =>
          }
        case _ =>
      }
    }
    if (count == 0) return Seq.empty
    if (count == 1) return Seq(psiClass)
    buffer.toSeq
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
    val values: util.Collection[_ <: PsiElement] =
      StubIndex.getInstance.get(ScalaIndexKeys.VALUE_NAME_KEY, name, project,
      new ScalaSourceFilterScope(scope, project))
    val list: ArrayBuffer[PsiMember] = new ArrayBuffer[PsiMember]
    var member: PsiMember = null
    var count: Int = 0
    val valuesIterator = values.iterator()
    while (valuesIterator.hasNext) {
      val element: PsiElement = valuesIterator.next()
      if (!ScalaStubsUtil.checkPsiForValue(element)) return Seq.empty
      member = element.asInstanceOf[PsiMember]
      if (element.asInstanceOf[ScValue].declaredNames.contains(name)) {
        list += member
        count += 1
      }
    }
    val variables: util.Collection[_ <: PsiElement] =
      StubIndex.getInstance.get(ScalaIndexKeys.VARIABLE_NAME_KEY, name, project,
        new ScalaSourceFilterScope(scope, project))
    val variablesIterator = variables.iterator()
    while (variablesIterator.hasNext) {
      val element = variablesIterator.next()
      if (!ScalaStubsUtil.checkPsiForVariable(element)) return Seq.empty
      member = element.asInstanceOf[PsiMember]
      if (element.asInstanceOf[ScVariable].declaredNames.contains(name)) {
        list += member
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
    def scalaMethods: Seq[PsiMethod] = {
      val methods: util.Collection[_ <: PsiElement] =
        StubIndex.getInstance.get(ScalaIndexKeys.METHOD_NAME_KEY, name, project, new ScalaSourceFilterScope(scope, project))
      val list: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
      var method: PsiMethod = null
      var count: Int = 0
      val methodsIterator = methods.iterator()
      while (methodsIterator.hasNext) {
        val element = methodsIterator.next()
        if (!ScalaStubsUtil.checkPsiForPsiMethod(element)) return Seq.empty
        method = element.asInstanceOf[PsiMethod]
        if (name == method.name) {
          list += method
          count += 1
        }
      }
      if (count == 0) Seq.empty
      if (count == 1) Seq(method)
      list.toSeq
    }
    def javaMethods: Seq[PsiMethod] = {
      PsiShortNamesCache.getInstance(project).getMethodsByName(name, scope).filter {
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

  def getClassesByName(name: String, scope: GlobalSearchScope): Seq[_ <: PsiElement] = {
    val plainClasses: util.Collection[_ <: PsiElement] =
      StubIndex.getInstance.get(ScalaIndexKeys.SHORT_NAME_KEY, name, project, scope)
    import scala.collection.JavaConversions._
    plainClasses.toSeq
  }

  def getPackageObjectByName(fqn: String, scope: GlobalSearchScope): ScTypeDefinition = {
    if (DumbService.getInstance(project).isDumb) return null

    val classes: util.Collection[_ <: PsiElement] =
      StubIndex.getInstance.get[java.lang.Integer, PsiClass](ScalaIndexKeys.PACKAGE_OBJECT_KEY,
        fqn.hashCode, project, scope)
    val classesIterator = classes.iterator()
    while (classesIterator.hasNext) {
      val element = classesIterator.next()
      if (!ScalaStubsUtil.checkPsiForClass(element)) return null
      val psiClass: PsiClass = element.asInstanceOf[PsiClass]
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
        if (fqn == qualifiedName) {
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
    val classes: util.Collection[_ <: PsiElement] = StubIndex.getInstance.get(ScalaIndexKeys.IMPLICIT_OBJECT_KEY, fqn, project,
      new ScalaSourceFilterScope(scope, project))
    val res: ArrayBuffer[ScObject] = new ArrayBuffer[ScObject]
    val classesIterator = classes.iterator()
    while (classesIterator.hasNext) {
      val element = classesIterator.next()
      if (!ScalaStubsUtil.checkPsiForObject(element)) return Seq.empty
      res += element.asInstanceOf[ScObject]
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
