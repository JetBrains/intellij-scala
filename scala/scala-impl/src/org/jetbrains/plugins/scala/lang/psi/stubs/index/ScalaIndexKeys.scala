package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StubIndex, StubIndexKey}
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.CommonProcessors.alwaysFalse
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * @author ilyas
 */
//noinspection TypeAnnotation
object ScalaIndexKeys {

  import StubIndexKey.createIndexKey

  val ALL_CLASS_NAMES = createIndexKey[String, PsiClass]("sc.all.class.names")
  val SHORT_NAME_KEY = createIndexKey[String, PsiClass]("sc.class.shortName")
  val NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY = createIndexKey[String, PsiClass]("sc.not.visible.in.java.class.shortName")
  val FQN_KEY = createIndexKey[Integer, PsiClass]("sc.class.fqn")
  val PACKAGE_OBJECT_KEY = createIndexKey[Integer, PsiClass]("sc.package.object.fqn")
  val PACKAGE_OBJECT_SHORT_NAME_KEY = createIndexKey[String, PsiClass]("sc.package.object.short")
  val PACKAGE_FQN_KEY = createIndexKey[Integer, ScPackaging]("sc.package.fqn")
  val METHOD_NAME_KEY = createIndexKey[String, ScFunction]("sc.method.name")
  val CLASS_NAME_IN_PACKAGE_KEY = createIndexKey[String, PsiClass]("sc.class.name.in.package")
  val JAVA_CLASS_NAME_IN_PACKAGE_KEY = createIndexKey[String, PsiClass]("sc.java.class.name.in.package")
  val IMPLICIT_OBJECT_KEY = createIndexKey[String, ScObject]("sc.implcit.object")
  val ANNOTATED_MEMBER_KEY = createIndexKey[String, ScAnnotation]("sc.annotatde.member.name")
  val PROPERTY_NAME_KEY = createIndexKey[String, ScValueOrVariable]("sc.property.name")
  val CLASS_PARAMETER_NAME_KEY = createIndexKey[String, ScClassParameter]("sc.class.parameter.name")
  val TYPE_ALIAS_NAME_KEY = createIndexKey[String, ScTypeAlias]("sc.type.alias.name")
  val STABLE_ALIAS_NAME_KEY = createIndexKey[String, ScTypeAlias]("sc.stable.alias.name")
  val STABLE_ALIAS_FQN_KEY = createIndexKey[Integer, ScTypeAlias]("sc.stable.alias.fqn")
  val SUPER_CLASS_NAME_KEY = createIndexKey[String, ScExtendsBlock]("sc.super.class.name")
  val SELF_TYPE_CLASS_NAME_KEY = createIndexKey[String, ScSelfTypeElement]("sc.self.type.class.name.key")

  //only implicit classes and implicit conversion defs are indexed
  //there is also a case when implicit conversion is provided by an implicit val with function type, but I think it is too exotic to support
  val IMPLICIT_CONVERSION_KEY = createIndexKey[String, ScMember]("sc.implicit.conversion")
  val IMPLICIT_INSTANCE_KEY = createIndexKey[String, ScMember]("sc.implicit.instance")

  implicit class StubIndexKeyExt[Key, Psi <: PsiElement](private val indexKey: StubIndexKey[Key, Psi]) extends AnyVal {

    import collection.JavaConverters._

    def elements(key: Key, scope: GlobalSearchScope,
                 requiredClass: Class[Psi])
                (implicit project: Project): Iterable[Psi] =
      StubIndex.getElements(
        indexKey,
        key,
        project,
        ScalaFilterScope(scope),
        requiredClass
      ).asScala

    def allKeys(implicit project: Project): Iterable[Key] =
      StubIndex.getInstance.getAllKeys(indexKey, project).asScala

    def hasElements(key: Key, scope: GlobalSearchScope,
                    requiredClass: Class[Psi])
                   (implicit project: Project): Boolean =
      !StubIndex.getInstance.processElements(
        indexKey,
        key,
        project,
        scope,
        requiredClass,
        alwaysFalse[Psi]
      ) // processElements will return true only there is no elements
  }

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