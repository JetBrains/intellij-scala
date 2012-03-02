package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import fake.FakePsiMethod
import typedef._
import types.result.{TypingContext, TypingContextOwner}
import com.intellij.openapi.util.text.StringUtil
import types.ScType
import statements.params.ScClassParameter
import statements.{ScAnnotationsHolder, ScVariable, ScValue}
import types.nonvalue.Parameter
import com.intellij.psi.{PsiElement, PsiClass, PsiMethod}
import com.intellij.util.containers.ConcurrentHashMap
import collection.mutable.ArrayBuffer
import light.{PsiClassWrapper, StaticPsiTypedDefinitionWrapper, PsiTypedDefinitionWrapper}

/**
 * Member definitions, classes, named patterns which have types
 */
trait ScTypedDefinition extends ScNamedElement with TypingContextOwner {

  /**
   * @return false for variable elements
   */
  def isStable = true
  
  @volatile
  private var beanMethodsCache: Seq[PsiMethod] = null
  @volatile
  private var modCount: Long = 0L

  def getBeanMethods: Seq[PsiMethod] = {
    implicit def arr2arr(a: Array[ScType]): Array[Parameter] = a.map(new Parameter("", _, false, false, false))
    def getBeanMethodsInner(t: ScTypedDefinition): Seq[PsiMethod] = {
      def valueSeq(v: ScAnnotationsHolder with ScModifierListOwner): Seq[PsiMethod] = {
        val beanProperty = v.hasAnnotation("scala.reflect.BeanProperty").isDefined
        val booleanBeanProperty = v.hasAnnotation("scala.reflect.BooleanBeanProperty").isDefined
        if (beanProperty || booleanBeanProperty) {
          val prefix = if (beanProperty) "get" else "is"
          Seq(new FakePsiMethod(t, prefix + StringUtil.capitalize(t.name), Array.empty,
            t.getType(TypingContext.empty).getOrAny, v.hasModifierProperty _))
        } else Seq.empty
      }
      def variableSeq(v: ScAnnotationsHolder with ScModifierListOwner): Seq[PsiMethod] = {
        val beanProperty = v.hasAnnotation("scala.reflect.BeanProperty").isDefined
        val booleanBeanProperty = v.hasAnnotation("scala.reflect.BooleanBeanProperty").isDefined
        if (beanProperty || booleanBeanProperty) {
          val prefix = if (beanProperty) "get" else "is"
          val tType = t.getType(TypingContext.empty).getOrAny
          val capName = StringUtil.capitalize(t.name)
          Seq(new FakePsiMethod(t, prefix + capName, Array.empty, tType, v.hasModifierProperty _),
          new FakePsiMethod(t, "set" + capName, Array[ScType](tType), types.Unit, v.hasModifierProperty _))
        } else Seq.empty
      }
      ScalaPsiUtil.nameContext(this) match {
        case v: ScValue =>
          valueSeq(v)
        case v: ScVariable =>
          variableSeq(v)
        case v: ScClassParameter if v.isVal =>
          valueSeq(v)
        case v: ScClassParameter if v.isVar =>
          variableSeq(v)
        case _ => Seq.empty
      }
    }
    
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (beanMethodsCache != null && modCount == curModCount) return beanMethodsCache
    val res = getBeanMethodsInner(this)
    modCount = curModCount
    beanMethodsCache = res
    res
  }

  import PsiTypedDefinitionWrapper.DefinitionRole._
  private var typedDefinitionWrapper: ConcurrentHashMap[(Boolean, Boolean, DefinitionRole, Option[PsiClass]), (PsiTypedDefinitionWrapper, Long)] =
    new ConcurrentHashMap()

  def getTypedDefinitionWrapper(isStatic: Boolean, isInterface: Boolean, role: DefinitionRole,
                                cClass: Option[PsiClass] = None): PsiTypedDefinitionWrapper = {
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    val r = typedDefinitionWrapper.get(isStatic, isInterface, role, cClass)
    if (r != null && r._2 == curModCount) {
      return r._1
    }
    val res = new PsiTypedDefinitionWrapper(this, isStatic, isInterface, role, cClass)
    typedDefinitionWrapper.put((isStatic, isInterface, role, cClass), (res, curModCount))
    res
  }

  private var staticTypedDefinitionWrapper: ConcurrentHashMap[(DefinitionRole, PsiClassWrapper), (StaticPsiTypedDefinitionWrapper, Long)] =
    new ConcurrentHashMap()

  def getStaticTypedDefinitionWrapper(role: DefinitionRole, cClass: PsiClassWrapper): StaticPsiTypedDefinitionWrapper = {
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    val r = staticTypedDefinitionWrapper.get(role, cClass)
    if (r != null && r._2 == curModCount) {
      return r._1
    }
    val res = new StaticPsiTypedDefinitionWrapper(this, role, cClass)
    staticTypedDefinitionWrapper.put((role, cClass), (res, curModCount))
    res
  }

  def nameContext: PsiElement = ScalaPsiUtil.nameContext(this)
  def isVar: Boolean = false
  def isVal: Boolean = false
}