package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiTypedDefinitionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{Typeable, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
 * Member definitions, classes, named patterns which have types
 */
trait ScTypedDefinition extends ScNamedElement with Typeable {

  /**
   * @return false for variable elements
   */
  def isStable = true

  private def typeArr2paramArr(a: Array[ScType]): Array[Parameter] = a.toSeq.mapWithIndex {
    case (tpe, index) => Parameter(tpe, isRepeated = false, index = index)
  }.toArray

  @Cached(synchronized = false, modificationCount = ModCount.getBlockModificationCount, this)
  def getUnderEqualsMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    val tType = getType(TypingContext.empty).getOrAny
    new FakePsiMethod(this, name + "_=", typeArr2paramArr(Array[ScType](tType)), Unit, hasModifierProperty)
  }

  @Cached(synchronized = false, modificationCount = ModCount.getBlockModificationCount, this)
  def getGetBeanMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    new FakePsiMethod(this, "get" + StringUtil.capitalize(this.name), Array.empty,
      this.getType(TypingContext.empty).getOrAny, hasModifierProperty)
  }

  @Cached(synchronized = false, modificationCount = ModCount.getBlockModificationCount, this)
  def getSetBeanMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    val tType = getType(TypingContext.empty).getOrAny
    new FakePsiMethod(this, "set" + name.capitalize, typeArr2paramArr(Array[ScType](tType)), api.Unit, hasModifierProperty)
  }

  @Cached(synchronized = false, modificationCount = ModCount.getBlockModificationCount, this)
  def getIsBeanMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    new FakePsiMethod(this, "is" + StringUtil.capitalize(this.name), Array.empty,
      this.getType(TypingContext.empty).getOrAny, hasModifierProperty)
  }

  @Cached(synchronized = false, modificationCount = ModCount.getBlockModificationCount, this)
  def getBeanMethods: Seq[PsiMethod] = {
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

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  @Cached(synchronized = false, modificationCount = ModCount.getBlockModificationCount, this)
  def getTypedDefinitionWrapper(isStatic: Boolean, isInterface: Boolean, role: DefinitionRole,
                                cClass: Option[PsiClass] = None): PsiTypedDefinitionWrapper = {
    new PsiTypedDefinitionWrapper(this, isStatic, isInterface, role, cClass)
  }

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  def getStaticTypedDefinitionWrapper(role: DefinitionRole, cClass: PsiClassWrapper): StaticPsiTypedDefinitionWrapper = {
    new StaticPsiTypedDefinitionWrapper(this, role, cClass)
  }

  def nameContext: PsiElement = ScalaPsiUtil.nameContext(this)
  def isVar: Boolean = false
  def isVal: Boolean = false

  def isAbstractMember: Boolean = ScalaPsiUtil.nameContext(this) match {
    case _: ScFunctionDefinition | _: ScPatternDefinition | _: ScVariableDefinition => false
    case _: ScClassParameter => false
    case _ => true
  }
}
