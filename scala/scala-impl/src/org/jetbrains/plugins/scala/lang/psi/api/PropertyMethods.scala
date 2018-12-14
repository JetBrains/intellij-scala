package org.jetbrains.plugins.scala.lang.psi.api

import java.util.concurrent.ConcurrentMap

import com.intellij.psi.PsiMethod
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.ConcurrentMapExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScAnnotationsHolder, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod.{getter, setter}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.Seq

object PropertyMethods extends Enumeration {
  type DefinitionRole = Value

  val SIMPLE_ROLE, GETTER, IS_GETTER, SETTER, EQ = Value

  private val beanMethods = Seq(GETTER, IS_GETTER, SETTER)

  def isSetter(role: DefinitionRole): Boolean = role == SETTER || role == EQ

  def methodName(propertyName: String, role: DefinitionRole): String = role match {
    case SIMPLE_ROLE => propertyName
    case GETTER      => beanGetterName(propertyName)
    case IS_GETTER   => booleanGetterName(propertyName)
    case SETTER      => beanSetterName(propertyName)
    case EQ          => scalaSetterName(propertyName)
  }

  def methodRole(methodName: String, propertyName: String): Option[DefinitionRole] = {
    if (methodName == propertyName) Some(SIMPLE_ROLE)
    else if (methodName == beanGetterName(propertyName)) Some(GETTER)
    else if (methodName == booleanGetterName(propertyName)) Some(IS_GETTER)
    else if (methodName == beanSetterName(propertyName)) Some(SETTER)
    else if (methodName == scalaSetterName(propertyName)) Some(EQ)
    else None
  }

  def javaMethodName(propertyName: String, role: DefinitionRole): String = {
    val javaName = ScalaNamesUtil.toJavaName(propertyName)
    role match {
      case EQ => scalaSetterJavaName(javaName)
      case _  => methodName(javaName, role)
    }
  }

  private val cache: ConcurrentMap[(ScTypedDefinition, DefinitionRole), Option[PsiMethod]] = ContainerUtil.newConcurrentMap()

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

  def scalaSetterName(name: String)  : String = name + "_="

  def scalaSetterJavaName(name: String)  : String = name + "_$eq"

  def beanSetterName(name: String)   : String = "set" + name.capitalize

  def beanGetterName(name: String)   : String = "get" + name.capitalize

  def booleanGetterName(name: String): String = "is" + name.capitalize

  def propertyMethodNames(name: String): Seq[String] =
    scalaSetterName(name) :: beanSetterName(name) :: beanGetterName(name) :: booleanGetterName(name) :: Nil

  def getPropertyMethod(t: ScTypedDefinition, role: DefinitionRole): Option[PsiMethod] = {
    if (!mayHavePropertyMethod(t, role))
      return None

    cache.atomicGetOrElseUpdate((t, role),
      getPropertyMethodImpl(t, t.`type`().getOrAny, role))
  }

  def getBeanMethods(t: ScTypedDefinition): Seq[PsiMethod] = beanMethods.flatMap(getPropertyMethod(t, _))

  def isProperty(t: ScTypedDefinition): Boolean = {
    t.nameContext match {
      case v: ScValueOrVariable => true
      case c: ScClassParameter if c.isClassMember => true
      case _ => false
    }
  }

  def mayHavePropertyMethod(t: ScTypedDefinition, role: DefinitionRole): Boolean = {
    isProperty(t) &&
      isSetter(role) == t.isVar &&
      (role == EQ || t.nameContext.asInstanceOf[ScMember].annotations.nonEmpty)
  }

  private def getPropertyMethodImpl(property: ScTypedDefinition,
                                    propertyType: ScType,
                                    role: DefinitionRole): Option[PsiMethod] = {
    val member = property.nameContext.asInstanceOf[ScMember]
    val isVar = property.isVar
    val mName = methodName(property.name, role)

    role match {
      case SIMPLE_ROLE                                => None
      case GETTER if isBeanProperty(member)           => Some(getter(property, mName))
      case IS_GETTER if isBooleanBeanProperty(member) => Some(getter(property, mName))
      case SETTER if isBeanProperty(member) && isVar  => Some(setter(property, mName))
      case EQ if isVar                                => Some(setter(property, mName))
      case _                                          => None
    }
  }
}
