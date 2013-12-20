package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import api.toplevel.packaging.ScPackageContainer

import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey
import api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember, ScTypeDefinition}
import api.statements.params.ScClassParameter
import api.base.types.ScSelfTypeElement

/**
 * @author ilyas
 */

object ScalaIndexKeys {

  val ALL_CLASS_NAMES: StubIndexKey[String, PsiClass] = StubIndexKey.createIndexKey("sc.all.class.names")
  val SHORT_NAME_KEY: StubIndexKey[String, PsiClass]  = StubIndexKey.createIndexKey("sc.class.shortName")
  val NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY: StubIndexKey[String, PsiClass]  = StubIndexKey.createIndexKey("sc.not.visible.in.java.class.shortName")
  val FQN_KEY: StubIndexKey[java.lang.Integer, PsiClass]  = StubIndexKey.createIndexKey("sc.class.fqn")
  val PACKAGE_OBJECT_KEY: StubIndexKey[java.lang.Integer, PsiClass]  = StubIndexKey.createIndexKey("sc.package.object.fqn")
  val PACKAGE_OBJECT_SHORT_NAME_KEY: StubIndexKey[String, PsiClass] = StubIndexKey.createIndexKey("sc.package.object.short")
  val PACKAGE_FQN_KEY: StubIndexKey[java.lang.Integer, ScPackageContainer]  = StubIndexKey.createIndexKey("sc.package.fqn")
  val METHOD_NAME_KEY: StubIndexKey[String, ScFunction] = StubIndexKey.createIndexKey("sc.method.name")
  val CLASS_NAME_IN_PACKAGE_KEY: StubIndexKey[String, PsiClass] = StubIndexKey.createIndexKey("sc.class.name.in.package")
  val JAVA_CLASS_NAME_IN_PACKAGE_KEY: StubIndexKey[String, PsiClass] = StubIndexKey.createIndexKey("sc.java.class.name.in.package")
  val IMPLICIT_OBJECT_KEY: StubIndexKey[String, ScObject] = StubIndexKey.createIndexKey("sc.implcit.object")
  @deprecated
  val METHOD_NAME_TO_CLASS_KEY: StubIndexKey[String, ScTypeDefinition] = StubIndexKey.createIndexKey("sc.method.name.class")
  val ANNOTATED_MEMBER_KEY: StubIndexKey[String, ScAnnotation] = StubIndexKey.createIndexKey("sc.annotatde.member.name")
  val VALUE_NAME_KEY: StubIndexKey[String, ScValue] = StubIndexKey.createIndexKey("sc.value.name")
  val VARIABLE_NAME_KEY: StubIndexKey[String, ScVariable] = StubIndexKey.createIndexKey("sc.variable.name")
  val CLASS_PARAMETER_NAME_KEY: StubIndexKey[String, ScClassParameter] = StubIndexKey.createIndexKey("sc.class.parameter.name")
  val TYPE_ALIAS_NAME_KEY: StubIndexKey[String, ScTypeAlias] = StubIndexKey.createIndexKey("sc.type.alias.name")
  val STABLE_ALIAS_NAME_KEY: StubIndexKey[String, ScTypeAlias] = StubIndexKey.createIndexKey("sc.stable.alias.name")
  val SUPER_CLASS_NAME_KEY: StubIndexKey[String, ScExtendsBlock] = StubIndexKey.createIndexKey("sc.super.class.name")
  val SELF_TYPE_CLASS_NAME_KEY: StubIndexKey[String, ScSelfTypeElement] = StubIndexKey.createIndexKey("sc.self.type.class.name.key")
  val IMPLICITS_KEY: StubIndexKey[String, ScMember] = StubIndexKey.createIndexKey("sc.implicit.function.name")
}