package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions.{ConcurrentMapExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod.{getter, setter}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.isBacktickedName.withoutBackticks

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

object PropertyMethods extends Enumeration {
  type DefinitionRole = Value

  val SIMPLE_ROLE, GETTER, IS_GETTER, SETTER, EQ = Value

  private val beanMethods = Seq(GETTER, IS_GETTER, SETTER)

  val allRoles: Seq[DefinitionRole] = values.toSeq

  def isSetter(role: DefinitionRole): Boolean = role == SETTER || role == EQ

  def methodName(propertyName: String, role: DefinitionRole): String = scalaMethodName(propertyName, decoration(role))

  def javaMethodName(propertyName: String, role: DefinitionRole): String = javaMethodName(propertyName, decoration(role))

  private def decoration(role: DefinitionRole): String => String = role match {
    case SIMPLE_ROLE => identity
    case GETTER      => "get" + _.capitalize
    case SETTER      => "set" + _.capitalize
    case IS_GETTER   => "is"  + _.capitalize
    case EQ          => _ + "_="
  }

  def methodRole(mName: String, propertyName: String): Option[DefinitionRole] = {
    values.find(mName == methodName(propertyName, _))
  }

  private val cache: ConcurrentMap[(ScTypedDefinition, DefinitionRole), Option[PsiMethod]] = new ConcurrentHashMap()

  def clearCache(): Unit = cache.clear()

  def isBooleanBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean =
    hasAnnotation(s, noResolve, "scala.reflect.BooleanBeanProperty", "scala.beans.BooleanBeanProperty")

  def isBeanProperty(s: ScAnnotationsHolder, noResolve: Boolean = false): Boolean =
    hasAnnotation(s, noResolve, "scala.reflect.BeanProperty", "scala.beans.BeanProperty")

  private def hasAnnotation(s: ScAnnotationsHolder, noResolve: Boolean, qualifiedNames: String*) = {
    qualifiedNames.exists { qName =>
      if (noResolve) s.findAnnotationNoAliases(qName) != null
      else s.hasAnnotation(qName)
    }
  }

  def isApplicable(role: DefinitionRole, t: ScTypedDefinition, noResolve: Boolean): Boolean = {
    def holder: Option[ScAnnotationsHolder] = t.nameContext.asOptionOf[ScAnnotationsHolder]
    role match {
      case SIMPLE_ROLE => true
      case EQ          => t.isVar
      case GETTER      => holder.exists(isBeanProperty(_, noResolve))
      case IS_GETTER   => holder.exists(isBooleanBeanProperty(_, noResolve))
      case SETTER      => t.isVar && holder.exists(h => isBeanProperty(h, noResolve) || isBooleanBeanProperty(h, noResolve))
    }
  }

  private def javaMethodName(scalaName: String, decoration: String => String): String = {
    toJavaName(decoration(withoutBackticks(scalaName)))
  }

  private def scalaMethodName(scalaName: String, decoration: String => String): String =
    clean(decoration(withoutBackticks(scalaName)))

  def getPropertyMethod(t: ScTypedDefinition, role: DefinitionRole): Option[PsiMethod] = {
    if (!mayHavePropertyMethod(t, role))
      return None

    cache.atomicGetOrElseUpdate((t, role),
      getPropertyMethodImpl(t, role))
  }

  def getBeanMethods(t: ScTypedDefinition): Seq[PsiMethod] = beanMethods.flatMap(getPropertyMethod(t, _))

  def isProperty(t: ScTypedDefinition): Boolean = {
    t.nameContext match {
      case _: ScValueOrVariable => true
      case c: ScClassParameter if c.isClassMember => true
      case _ => false
    }
  }

  def mayHavePropertyMethod(t: ScTypedDefinition, role: DefinitionRole): Boolean = {
    isProperty(t) &&
      (!isSetter(role) || t.isVar) &&
      (role == EQ || t.nameContext.asInstanceOf[ScMember].annotations.nonEmpty)
  }

  private def getPropertyMethodImpl(property: ScTypedDefinition,
                                    role: DefinitionRole): Option[PsiMethod] = {
    val member = property.nameContext.asInstanceOf[ScMember]
    val isVar = property.isVar
    val mName = methodName(property.name, role)
    def shouldHaveBeanSetter(member: ScMember) = isVar && (isBeanProperty(member) || isBooleanBeanProperty(member))

    role match {
      case SIMPLE_ROLE                                => None
      case GETTER if isBeanProperty(member)           => Some(getter(property, mName))
      case IS_GETTER if isBooleanBeanProperty(member) => Some(getter(property, mName))
      case SETTER if shouldHaveBeanSetter(member)     => Some(setter(property, mName))
      case EQ if isVar                                => Some(setter(property, mName))
      case _                                          => None
    }
  }
}
