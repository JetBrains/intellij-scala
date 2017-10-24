package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}

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
  val PACKAGE_FQN_KEY: StubIndexKey[java.lang.Integer, ScPackaging] = StubIndexKey.createIndexKey("sc.package.fqn")
  val METHOD_NAME_KEY: StubIndexKey[String, ScFunction] = StubIndexKey.createIndexKey("sc.method.name")
  val CLASS_NAME_IN_PACKAGE_KEY: StubIndexKey[String, PsiClass] = StubIndexKey.createIndexKey("sc.class.name.in.package")
  val JAVA_CLASS_NAME_IN_PACKAGE_KEY: StubIndexKey[String, PsiClass] = StubIndexKey.createIndexKey("sc.java.class.name.in.package")
  val IMPLICIT_OBJECT_KEY: StubIndexKey[String, ScObject] = StubIndexKey.createIndexKey("sc.implcit.object")
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