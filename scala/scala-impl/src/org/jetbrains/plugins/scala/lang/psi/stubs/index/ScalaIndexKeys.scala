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
import org.jetbrains.plugins.scala.extensions.CollectUniquesProcessorEx
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGiven, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.reflect.ClassTag

//noinspection TypeAnnotation
object ScalaIndexKeys {

  import StubIndexKey.createIndexKey

  val ALL_CLASS_NAMES                     = createIndexKey[String, PsiClass]("sc.all.class.names")
  val SHORT_NAME_KEY                      = createIndexKey[String, PsiClass]("sc.class.shortName")
  val NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY  = createIndexKey[String, PsiClass]("sc.not.visible.in.java.class.shortName")
  val FQN_KEY                             = createIndexKey[Integer, PsiClass]("sc.class.fqn")
  val PACKAGE_OBJECT_KEY                  = createIndexKey[Integer, PsiClass]("sc.package.object.fqn")
  val PACKAGE_OBJECT_SHORT_NAME_KEY       = createIndexKey[String, PsiClass]("sc.package.object.short")
  val PACKAGE_FQN_KEY                     = createIndexKey[Integer, ScPackaging]("sc.package.fqn")
  val METHOD_NAME_KEY                     = createIndexKey[String, ScFunction]("sc.method.name")
  val CLASS_NAME_IN_PACKAGE_KEY           = createIndexKey[String, PsiClass]("sc.class.name.in.package")
  val JAVA_CLASS_NAME_IN_PACKAGE_KEY      = createIndexKey[String, PsiClass]("sc.java.class.name.in.package")
  val IMPLICIT_OBJECT_KEY                 = createIndexKey[String, ScObject]("sc.implicit.object")
  val ANNOTATED_MEMBER_KEY                = createIndexKey[String, ScAnnotation]("sc.annotated.member.name")
  val PROPERTY_NAME_KEY                   = createIndexKey[String, ScValueOrVariable]("sc.property.name")
  val PROPERTY_CLASS_NAME_KEY             = createIndexKey[String, ScValueOrVariable]("sc.property.class.name")
  val CLASS_PARAMETER_NAME_KEY            = createIndexKey[String, ScClassParameter]("sc.class.parameter.name")
  val TYPE_ALIAS_NAME_KEY                 = createIndexKey[String, ScTypeAlias]("sc.type.alias.name")
  val STABLE_ALIAS_NAME_KEY               = createIndexKey[String, ScTypeAlias]("sc.stable.alias.name")
  val STABLE_ALIAS_FQN_KEY                = createIndexKey[Integer, ScTypeAlias]("sc.stable.alias.fqn")
  val ALIASED_CLASS_NAME_KEY              = createIndexKey[String, ScTypeAlias]("sc.aliased.class.name")
  val SUPER_CLASS_NAME_KEY                = createIndexKey[String, ScExtendsBlock]("sc.super.class.name")
  val SELF_TYPE_CLASS_NAME_KEY            = createIndexKey[String, ScSelfTypeElement]("sc.self.type.class.name.key")
  val TOP_LEVEL_TYPE_ALIAS_BY_PKG_KEY     = createIndexKey[String, ScTypeAlias]("sc.top.level.alias.by.package.key")
  val TOP_LEVEL_VAL_OR_VAR_BY_PKG_KEY     = createIndexKey[String, ScValueOrVariable]("sc.top.level.valvar.by.package.key")
  val TOP_LEVEL_FUNCTION_BY_PKG_KEY       = createIndexKey[String, ScFunction]("sc.top.level.function.by.package.key")
  val TOP_LEVEL_IMPLICIT_CLASS_BY_PKG_KEY = createIndexKey[String, ScClass]("sc.top.level.implicit.class.by.package.key")
  val TOP_LEVEL_EXTENSION_BY_PKG_KEY      = createIndexKey[String, ScExtension]("sc.top.level.extension.by.package.key")
  val ALIASED_IMPORT_KEY                  = createIndexKey[String, ScImportSelector]("sc.aliased.import.key")

  // Scala3 @main methods
  val ANNOTATED_MAIN_FUNCTION_BY_PKG_KEY  = createIndexKey[String, ScFunction]("sc.annotated.main.function.by.package.key")

  //only implicit classes and implicit conversion defs are indexed
  //there is also a case when implicit conversion is provided by an implicit val with function type, but I think it is too exotic to support
  val IMPLICIT_CONVERSION_KEY = createIndexKey[String, ScMember]("sc.implicit.conversion")
  val IMPLICIT_INSTANCE_KEY   = createIndexKey[String, ScMember]("sc.implicit.instance")

  val EXTENSION_KEY = createIndexKey[String, ScExtension]("sc.extension")
  val GIVEN_KEY = createIndexKey[String, ScGiven]("sc.implicit.given")

  implicit class StubIndexKeyExt[Key, Psi <: PsiElement: ClassTag](private val indexKey: StubIndexKey[Key, Psi]) {

    import scala.jdk.CollectionConverters._

    def elements(key: Key, scope: GlobalSearchScope)
                (implicit project: Project): Iterable[Psi] =
      StubIndex.getElements(
        indexKey,
        key,
        project,
        ScalaFilterScope(scope),
        implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]]
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

  implicit class StubIndexIntegerKeyExt[Psi <: PsiElement : ClassTag](private val indexKey: StubIndexKey[Integer, Psi]) {

    private def key(fqn: String): Integer = ScalaNamesUtil.cleanFqn(fqn).hashCode

    def allElementsByHash(name: String)
                         (implicit project: Project): Iterable[Psi] =
      elementsByHash(name, GlobalSearchScope.allScope(project))

    def elementsByHash(name: String, scope: GlobalSearchScope)
                      (implicit project: Project): Iterable[Psi] =
      indexKey.elements(key(name), scope)

    def hasIntegerElements(name: String, scope: GlobalSearchScope, requiredClass: Class[Psi])
                          (implicit project: Project): Boolean =
      indexKey.hasElements(key(name), scope, requiredClass)
  }

  implicit class StubIndexStringKeyExt[Psi <: PsiElement : ClassTag](private val indexKey: StubIndexKey[String, Psi]) {

    def forClassFqn(qualifiedName: String, scope: GlobalSearchScope)
                   (implicit project: Project): Set[Psi] = {
      val stubIndex = StubIndex.getInstance
      val collectProcessor = new CollectUniquesProcessorEx[Psi]

      for {
        segments <- ScalaNamesUtil.splitName(qualifiedName).tails
        if segments.nonEmpty

        name = segments.mkString(".")
      } stubIndex.processElements(
        indexKey,
        name,
        project,
        scope,
        implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]],
        collectProcessor
      )

      collectProcessor.results
    }

  }

}
