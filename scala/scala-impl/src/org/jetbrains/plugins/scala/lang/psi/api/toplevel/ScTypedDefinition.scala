package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiClass, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiTypedDefinitionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInsidePsiElement, ModCount}

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

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def getUnderEqualsMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    val tType = getType().getOrAny
    new FakePsiMethod(this, name + "_=", typeArr2paramArr(Array[ScType](tType)), Unit, hasModifierProperty)
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def getGetBeanMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    new FakePsiMethod(this, "get" + StringUtil.capitalize(this.name), Array.empty,
      this.getType().getOrAny, hasModifierProperty)
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def getSetBeanMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    val tType = getType().getOrAny
    new FakePsiMethod(this, "set" + name.capitalize, typeArr2paramArr(Array[ScType](tType)), api.Unit, hasModifierProperty)
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def getIsBeanMethod: PsiMethod = {
    val hasModifierProperty: String => Boolean = nameContext match {
      case v: ScModifierListOwner => v.hasModifierProperty
      case _ => _ => false
    }
    new FakePsiMethod(this, "is" + StringUtil.capitalize(this.name), Array.empty,
      this.getType().getOrAny, hasModifierProperty)
  }

  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def getBeanMethods: Seq[PsiMethod] = {
    val (member, needSetter) = nameContext match {
      case v: ScValue => (v, false)
      case v: ScVariable => (v, true)
      case cp: ScClassParameter if cp.isEffectiveVal => (cp, cp.isVar)
      case _ => return Nil
    }
    val beanProperty = ScalaPsiUtil.isBeanProperty(member)
    val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(member)
    val getter =
      if (beanProperty) List(getGetBeanMethod)
      else if (booleanBeanProperty) List(getIsBeanMethod)
      else Nil
    val setter =
      if ((beanProperty || booleanBeanProperty) && needSetter) List(getSetBeanMethod)
      else Nil
    getter ::: setter
  }

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  @Cached(ModCount.getBlockModificationCount, this)
  def getTypedDefinitionWrapper(isStatic: Boolean, isInterface: Boolean, role: DefinitionRole,
                                cClass: Option[PsiClass] = None): PsiTypedDefinitionWrapper = {
    new PsiTypedDefinitionWrapper(this, isStatic, isInterface, role, cClass)
  }

  @Cached(ModCount.getBlockModificationCount, this)
  def getStaticTypedDefinitionWrapper(role: DefinitionRole, cClass: PsiClassWrapper): StaticPsiTypedDefinitionWrapper = {
    new StaticPsiTypedDefinitionWrapper(this, role, cClass)
  }

  def isVar: Boolean = false
  def isVal: Boolean = false

  def isAbstractMember: Boolean = nameContext match {
    case _: ScFunctionDefinition | _: ScPatternDefinition | _: ScVariableDefinition => false
    case _: ScClassParameter => false
    case _ => true
  }
}
