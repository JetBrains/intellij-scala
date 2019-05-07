package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey.createIndexKey
import com.intellij.psi.stubs.{IndexSink, StubIndex, StubIndexKey}
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.CommonProcessors
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.JavaConverters

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

  //only implicit classes and implicit conversion defs are indexed
  //there is also a case when implicit conversion is provided by an implicit val with function type, but I think it is too exotic to support
  //no meaningful keys are provided, we just need to be able to enumerate all implicit conversions in a project
  val IMPLICIT_CONVERSION_KEY: StubIndexKey[String, ScMember] = createIndexKey("sc.implicit.conversion")

  implicit class StubIndexKeyExt[Key, Psi <: PsiElement](private val indexKey: StubIndexKey[Key, Psi]) extends AnyVal {

    import StubIndex._

    import JavaConverters._

    def elements(key: Key, scope: GlobalSearchScope,
                 requiredClass: Class[Psi])
                (implicit context: ProjectContext): Iterable[Psi] =
      getElements(indexKey,
        key,
        context,
        ScalaFilterScope(context, scope),
        requiredClass
      ).asScala

    def allKeys(implicit project: Project): Iterable[Key] =
      getInstance.getAllKeys(indexKey, project).asScala

    def hasElements(key: Key, scope: GlobalSearchScope, requiredClass: Class[Psi])
                   (implicit project: Project): Boolean = {

      //processElements will return true only there is no elements
      val noElementsExistsProcessor = CommonProcessors.alwaysFalse[Psi]()

      !getInstance().processElements(indexKey, key, project, scope, requiredClass, noElementsExistsProcessor)
    }
  }

  implicit class ImplicitConversionKeyExt(private val indexKey: IMPLICIT_CONVERSION_KEY.type) extends AnyVal {
    private def key: String = "implicit_conversion"

    def allElements(scope: GlobalSearchScope)
                   (implicit context: ProjectContext): Iterable[ScMember] =
      indexKey.elements(key, scope, classOf[ScMember])

    def occurence(sink: IndexSink): Unit =
      sink.occurrence(indexKey, key)
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