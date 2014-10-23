package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.util.containers.ConcurrentHashMap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiTypedDefinitionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, TypingContextOwner}

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

  @volatile
  private var isBeanMethodsCache: PsiMethod = null
  @volatile
  private var isModCount: Long = 0L

  @volatile
  private var getBeanMethodsCache: PsiMethod = null
  @volatile
  private var getModCount: Long = 0L

  @volatile
  private var setBeanMethodsCache: PsiMethod = null
  @volatile
  private var setModCount: Long = 0L

  @volatile
  private var underEqualsMethodsCache: PsiMethod = null
  @volatile
  private var underEqualsModCount: Long = 0L

  private def typeArr2paramArr(a: Array[ScType]): Array[Parameter] = a.toSeq.mapWithIndex {
    case (tpe, index) => new Parameter("", None, tpe, false, false, false, index)
  }.toArray

  def getUnderEqualsMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty
        case _ => _ => false
      }
      val tType = getType(TypingContext.empty).getOrAny
      new FakePsiMethod(this, name + "_=", typeArr2paramArr(Array[ScType](tType)), types.Unit, hasModifierProperty)
    }

    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (underEqualsMethodsCache != null && underEqualsModCount == curModCount) return underEqualsMethodsCache
    val res = inner()
    underEqualsModCount = curModCount
    underEqualsMethodsCache = res
    res
  }

  def getGetBeanMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty
        case _ => _ => false
      }
      new FakePsiMethod(this, "get" + StringUtil.capitalize(this.name), Array.empty,
        this.getType(TypingContext.empty).getOrAny, hasModifierProperty)
    }

    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (getBeanMethodsCache != null && getModCount == curModCount) return getBeanMethodsCache
    val res = inner()
    getModCount = curModCount
    getBeanMethodsCache = res
    res
  }

  def getSetBeanMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty
        case _ => _ => false
      }
      val tType = getType(TypingContext.empty).getOrAny
      new FakePsiMethod(this, "set" + name.capitalize, typeArr2paramArr(Array[ScType](tType)), types.Unit, hasModifierProperty)
    }

    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (setBeanMethodsCache != null && setModCount == curModCount) return setBeanMethodsCache
    val res = inner()
    setModCount = curModCount
    setBeanMethodsCache = res
    res
  }

  def getIsBeanMethod: PsiMethod = {
    def inner(): PsiMethod = {
      val hasModifierProperty: String => Boolean = nameContext match {
        case v: ScModifierListOwner => v.hasModifierProperty
        case _ => _ => false
      }
      new FakePsiMethod(this, "is" + StringUtil.capitalize(this.name), Array.empty,
        this.getType(TypingContext.empty).getOrAny, hasModifierProperty)
    }

    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (isBeanMethodsCache != null && isModCount == curModCount) return isBeanMethodsCache
    val res = inner()
    isModCount = curModCount
    isBeanMethodsCache = res
    res
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
    
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (beanMethodsCache != null && modCount == curModCount) return beanMethodsCache
    val res = getBeanMethodsInner(this)
    modCount = curModCount
    beanMethodsCache = res
    res
  }

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
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

  def isAbstractMember: Boolean = ScalaPsiUtil.nameContext(this) match {
    case _: ScFunctionDefinition | _: ScPatternDefinition | _: ScVariableDefinition => false
    case cp: ScClassParameter if cp.isCaseClassVal => false
    case _ => true
  }
}