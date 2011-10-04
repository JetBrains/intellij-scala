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
import com.intellij.psi.PsiMethod
import statements.{ScAnnotationsHolder, ScVariable, ScValue}
import types.nonvalue.Parameter

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
}