package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import fake.FakePsiMethod
import types.result.{TypingContext, TypingContextOwner}
import com.intellij.openapi.util.text.StringUtil
import types.ScType
import statements.params.ScClassParameter
import statements.{ScAnnotationsHolder, ScVariable, ScValue}
import types.nonvalue.Parameter
import com.intellij.psi.{PsiElement, PsiClass, PsiMethod}
import com.intellij.util.containers.ConcurrentHashMap
import light.{PsiClassWrapper, StaticPsiTypedDefinitionWrapper, PsiTypedDefinitionWrapper}
import extensions.toSeqExt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * Member definitions, classes, named patterns which have types
 */
trait ScTypedDefinition extends ScNamedElement with TypingContextOwner {

  /**
   * @return false for variable elements
   */
  def isStable = true

  def getUnderEqualsMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty _
        case _ => _ => false
      }
      val tType = getType(TypingContext.empty).getOrAny
      implicit def arr2arr(a: Array[ScType]): Array[Parameter] = a.toSeq.mapWithIndex {
        case (tpe, index) => new Parameter("", None, tpe, false, false, false, index)
      }.toArray
      new FakePsiMethod(this, name + "_=", Array[ScType](tType), types.Unit, hasModifierProperty)
    }

    ScalaPsiManager.getOutOfCodeBlockCaches(ScalaPsiManager.underEqualsMethodsKey, this) {
      _ => inner()
    }
  }

  def getGetBeanMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty _
        case _ => _ => false
      }
      new FakePsiMethod(this, "get" + StringUtil.capitalize(this.name), Array.empty,
        this.getType(TypingContext.empty).getOrAny, hasModifierProperty)
    }

    ScalaPsiManager.getOutOfCodeBlockCaches(ScalaPsiManager.getBeanMethodsKey, this) {
      _ => inner()
    }
  }

  def getSetBeanMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty _
        case _ => _ => false
      }
      val tType = getType(TypingContext.empty).getOrAny
      implicit def arr2arr(a: Array[ScType]): Array[Parameter] = a.toSeq.mapWithIndex {
        case (tpe, index) => new Parameter("", None, tpe, false, false, false, index)
      }.toArray
      new FakePsiMethod(this, "set" + name.capitalize, Array[ScType](tType), types.Unit, hasModifierProperty)
    }

    ScalaPsiManager.getOutOfCodeBlockCaches(ScalaPsiManager.setBeanMethodsKey, this) {
      _ => inner()
    }
  }

  def getIsBeanMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty _
        case _ => _ => false
      }
      new FakePsiMethod(this, "is" + StringUtil.capitalize(this.name), Array.empty,
        this.getType(TypingContext.empty).getOrAny, hasModifierProperty)
    }

    ScalaPsiManager.getOutOfCodeBlockCaches(ScalaPsiManager.isBeanMethodKey, this) {
      _ => inner()
    }
  }

  def getBeanMethods: Seq[PsiMethod] = {
    def getBeanMethodsInner(t: ScTypedDefinition): Seq[PsiMethod] = {
      def valueSeq(v: ScAnnotationsHolder with ScModifierListOwner): Seq[PsiMethod] = {
        val beanProperty = ScalaPsiUtil.isBeanProperty(v)
        val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(v)
        if (beanProperty || booleanBeanProperty) {
          Seq(if (beanProperty) getGetBeanMethod else getIsBeanMethod)
        } else Seq.empty
      }
      def variableSeq(v: ScAnnotationsHolder with ScModifierListOwner): Seq[PsiMethod] = {
        val beanProperty = ScalaPsiUtil.isBeanProperty(v)
        val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(v)
        if (beanProperty || booleanBeanProperty) {
          Seq(if (beanProperty) getGetBeanMethod else getIsBeanMethod, getSetBeanMethod)
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
    
    ScalaPsiManager.getOutOfCodeBlockCaches(ScalaPsiManager.beanMethodsKey, this) {
      t => getBeanMethodsInner(t)
    }
  }

  import PsiTypedDefinitionWrapper.DefinitionRole._
  private val typedDefinitionWrapper: ConcurrentHashMap[(Boolean, Boolean, DefinitionRole, Option[PsiClass]), (PsiTypedDefinitionWrapper, Long)] =
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

  private val staticTypedDefinitionWrapper: ConcurrentHashMap[(DefinitionRole, PsiClassWrapper), (StaticPsiTypedDefinitionWrapper, Long)] =
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