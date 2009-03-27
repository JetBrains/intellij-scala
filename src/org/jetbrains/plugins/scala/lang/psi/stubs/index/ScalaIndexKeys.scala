package org.jetbrains.plugins.scala.lang.psi.stubs.index

import api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import api.toplevel.packaging.ScPackageContainer

import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

/**
 * @author ilyas
 */

object ScalaIndexKeys {

  val SHORT_NAME_KEY: StubIndexKey[String, PsiClass]  = StubIndexKey.createIndexKey("sc.class.shortName")
  val FQN_KEY: StubIndexKey[java.lang.Integer, PsiClass]  = StubIndexKey.createIndexKey("sc.class.fqn")
  val PACKAGE_FQN_KEY: StubIndexKey[java.lang.Integer, ScPackageContainer]  = StubIndexKey.createIndexKey("sc.package.fqn")
  val METHOD_NAME_KEY: StubIndexKey[String, ScFunction] = StubIndexKey.createIndexKey("sc.method.name")
  val METHOD_NAME_TO_CLASS_KEY: StubIndexKey[String, ScTypeDefinition] = StubIndexKey.createIndexKey("sc.method.name.class")
  val ANNOTATED_MEMBER_KEY: StubIndexKey[String, ScMember] = StubIndexKey.createIndexKey("sc.annotatde.member.name")
  val VALUE_NAME_KEY: StubIndexKey[String, ScValue] = StubIndexKey.createIndexKey("sc.value.name")
  val VARIABLE_NAME_KEY: StubIndexKey[String, ScVariable] = StubIndexKey.createIndexKey("sc.variable.name")
  val TYPE_ALIAS_NAME_KEY: StubIndexKey[String, ScTypeAlias] = StubIndexKey.createIndexKey("sc.type.alias.name")
  val SUPER_CLASS_NAME_KEY: StubIndexKey[String, ScExtendsBlock] = StubIndexKey.createIndexKey("sc.super.class.name")
}