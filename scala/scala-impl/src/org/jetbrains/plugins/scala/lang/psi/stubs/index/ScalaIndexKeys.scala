package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.stubs.StubIndexKey.createIndexKey
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * @author ilyas
 */
object ScalaIndexKeys {

  val ALL_CLASS_NAMES: StubIndexKey[String, PsiClass] = createIndexKey("sc.all.class.names")
  val SHORT_NAME_KEY: StubIndexKey[String, PsiClass] = createIndexKey("sc.class.shortName")
  val NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY: StubIndexKey[String, PsiClass] = createIndexKey("sc.not.visible.in.java.class.shortName")
  val FQN_KEY: StubIndexKey[Integer, PsiClass] = createIndexKey("sc.class.fqn")
  val PACKAGE_OBJECT_KEY: StubIndexKey[Integer, PsiClass] = createIndexKey("sc.package.object.fqn")
  val PACKAGE_OBJECT_SHORT_NAME_KEY: StubIndexKey[String, PsiClass] = createIndexKey("sc.package.object.short")
  val PACKAGE_FQN_KEY: StubIndexKey[Integer, ScPackaging] = createIndexKey("sc.package.fqn")
  val METHOD_NAME_KEY: StubIndexKey[String, ScFunction] = createIndexKey("sc.method.name")
  val CLASS_NAME_IN_PACKAGE_KEY: StubIndexKey[String, PsiClass] = createIndexKey("sc.class.name.in.package")
  val JAVA_CLASS_NAME_IN_PACKAGE_KEY: StubIndexKey[String, PsiClass] = createIndexKey("sc.java.class.name.in.package")
  val IMPLICIT_OBJECT_KEY: StubIndexKey[String, ScObject] = createIndexKey("sc.implcit.object")
  val ANNOTATED_MEMBER_KEY: StubIndexKey[String, ScAnnotation] = createIndexKey("sc.annotatde.member.name")
  val PROPERTY_NAME_KEY: StubIndexKey[String, ScValueOrVariable] = createIndexKey("sc.property.name")
  val CLASS_PARAMETER_NAME_KEY: StubIndexKey[String, ScClassParameter] = createIndexKey("sc.class.parameter.name")
  val TYPE_ALIAS_NAME_KEY: StubIndexKey[String, ScTypeAlias] = createIndexKey("sc.type.alias.name")
  val STABLE_ALIAS_NAME_KEY: StubIndexKey[String, ScTypeAlias] = createIndexKey("sc.stable.alias.name")
  val SUPER_CLASS_NAME_KEY: StubIndexKey[String, ScExtendsBlock] = createIndexKey("sc.super.class.name")
  val SELF_TYPE_CLASS_NAME_KEY: StubIndexKey[String, ScSelfTypeElement] = createIndexKey("sc.self.type.class.name.key")

  implicit class StubIndexKeyExt[Key, Psi <: PsiElement](val indexKey: StubIndexKey[Key, Psi])
    extends AnyVal with StubIndexExt[Key, Psi]

  implicit class StubIndexIntegerKeyExt[Psi <: PsiElement](private val indexKey: StubIndexKey[Integer, Psi]) extends AnyVal {

    private def key(fqn: String): Integer = ScalaNamesUtil.cleanFqn(fqn).hashCode

    def integerElements(name: String, requiredClass: Class[Psi])
                       (implicit project: Project): Iterable[Psi] =
      integerElements(name, GlobalSearchScope.allScope(project), requiredClass)

    def integerElements(name: String, scope: GlobalSearchScope, requiredClass: Class[Psi])
                       (implicit project: Project): Iterable[Psi] =
      indexKey.elements(key(name), scope, requiredClass)

    def hasIntegerElements(name: String, scope: GlobalSearchScope, requiredClass: Class[Psi])
                          (implicit project: Project): Boolean =
      indexKey.hasElements(key(name), scope, requiredClass)
  }
}