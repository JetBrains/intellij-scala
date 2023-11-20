package org.jetbrains.sbt
package project

import com.intellij.openapi.roots.DependencyScope
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.project.external.SdkReference

import java.net.URI
import scala.language.implicitConversions

/**
 * See also [[org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl]]
 */
object ProjectStructureDsl {

  import DslUtils._

  trait ProjectAttribute
  trait ModuleAttribute
  trait LibraryAttribute
  trait DependencyAttribute

  object libraries               extends Attribute[Seq[library]]("libraries")          with ProjectAttribute with ModuleAttribute
  object modules                 extends Attribute[Seq[module]]("modules")             with ProjectAttribute
  object sdk                     extends Attribute[SdkReference]("sdk")                with ProjectAttribute with ModuleAttribute
  object javaLanguageLevel       extends Attribute[LanguageLevel]("javaLanguageLevel") with ProjectAttribute with ModuleAttribute
  object javaTargetBytecodeLevel extends Attribute[String]("javaTargetBytecodeLevel")  with ProjectAttribute with ModuleAttribute
  object javacOptions            extends Attribute[Seq[String]]("javacOptions")        with ProjectAttribute with ModuleAttribute

  // looks like currently package prefix is a project-level feature (?)
  object packagePrefix           extends Attribute[String]("packagePrefix") with ProjectAttribute

  object sbtBuildURI         extends Attribute[URI]("sbtBuildURI")                              with ModuleAttribute
  object sbtProjectId        extends Attribute[String]("sbtProjectId")                          with ModuleAttribute
  object contentRoots        extends Attribute[Seq[String]]("contentRoots")                     with ModuleAttribute
  object sources             extends Attribute[Seq[String]]("sources")                          with ModuleAttribute
  object testSources         extends Attribute[Seq[String]]("testSources")                      with ModuleAttribute
  object resources           extends Attribute[Seq[String]]("resources")                        with ModuleAttribute
  object testResources       extends Attribute[Seq[String]]("testResources")                    with ModuleAttribute
  object excluded            extends Attribute[Seq[String]]("excluded")                         with ModuleAttribute
  object moduleDependencies  extends Attribute[Seq[dependency[module]]]("moduleDependencies")   with ModuleAttribute
  object libraryDependencies extends Attribute[Seq[dependency[library]]]("libraryDependencies") with ModuleAttribute
  object compileOrder extends Attribute[CompileOrder]("compileOrder") with ModuleAttribute

  object libClasses       extends Attribute[Seq[String]]("libraryClasses")                         with LibraryAttribute
  object libSources       extends Attribute[Seq[String]]("librarySources")                         with LibraryAttribute
  object libJavadocs      extends Attribute[Seq[String]]("libraryJavadocs")                        with LibraryAttribute
  object scalaSdkSettings extends Attribute[Option[ScalaSdkAttributes]]("scalaSdkSettings") with LibraryAttribute

  case class ScalaSdkAttributes(languageLevel: ScalaLanguageLevel, classpath: Option[Seq[String]], extraClasspath: Option[Seq[String]])
  object ScalaSdkAttributes {
    def apply(languageLevel: ScalaLanguageLevel, classpath: Option[Seq[String]]): ScalaSdkAttributes =
      new ScalaSdkAttributes(languageLevel, classpath, None)

    def apply(languageLevel: ScalaLanguageLevel, classpath: Seq[String]): ScalaSdkAttributes =
      new ScalaSdkAttributes(languageLevel, Some(classpath), None)

    def apply(languageLevel: ScalaLanguageLevel, classpath: Seq[String], extraClasspath: Seq[String]): ScalaSdkAttributes =
      new ScalaSdkAttributes(languageLevel, Some(classpath), Some(extraClasspath))
  }

  object isExported extends Attribute[Boolean]("isExported")    with DependencyAttribute
  object scope      extends Attribute[DependencyScope]("scope") with DependencyAttribute

  sealed trait Attributed {
    protected val attributes = new AttributeMap

    def foreach[T : Manifest](attribute: Attribute[T])(body: T => Option[MatchType] => Unit): Unit =
      attributes.get(attribute).foreach { attributeValue: T =>
        body(attributeValue)(attributes.getMatchType(attribute))
      }

    def foreach0[T : Manifest](attribute: Attribute[T])(body: T => Unit): Unit =
      attributes.get(attribute).foreach(body)

    def get[T: Manifest](attribute: Attribute[T]): Option[T] =
      attributes.get(attribute)

    protected implicit def matchTypeDef(attribute: Attribute[_]): MatchTypeDef =
      new MatchTypeDef(attribute, attributes)
  }

  trait Named {
    val name: String
  }

  class project(val name: String) extends Attributed {
    protected implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with ProjectAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
    protected implicit def defineAttributeSeq[T](attribute: Attribute[Seq[T]] with ProjectAttribute)(implicit m: Manifest[Seq[T]]): AttributeSeqDef[T] =
      new AttributeSeqDef(attribute, attributes)
  }

  class module(override val name: String, var group: Array[String] = null) extends Attributed with Named {
    implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with ModuleAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
    implicit def defineAttributeSeq[T](attribute: Attribute[Seq[T]] with ModuleAttribute)(implicit m: Manifest[Seq[T]]): AttributeSeqDef[T] =
      new AttributeSeqDef(attribute, attributes)

    def dependsOn(modules: dependency[module]*): Unit = {
      defineAttribute(moduleDependencies) := modules
    }

    def isBuildModule: Boolean =
      name.contains("-build")
  }

  class library(override val name: String) extends Attributed with Named {
    protected implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with LibraryAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
    protected implicit def defineAttributeSeq[T](attribute: Attribute[Seq[T]] with LibraryAttribute)(implicit m: Manifest[Seq[T]]): AttributeSeqDef[T] =
      new AttributeSeqDef(attribute, attributes)
  }

  class dependency[D <: Named](val reference: D) extends Attributed with Named {
    override val name: String = reference.name
    protected implicit def defineAttribute[T : Manifest](attribute: Attribute[T] with DependencyAttribute): AttributeDef[T] =
      new AttributeDef(attribute, attributes)
  }

  implicit def module2moduleDependency(module: module): dependency[module] =
    new dependency(module)

  implicit def library2libraryDependency(library: library): dependency[library] =
    new dependency(library)

  implicit def libraries2libraryDependencies(libraries: Seq[library]): Seq[dependency[library]] =
    libraries.map(library2libraryDependency)

  def library2libraryDependency(library: library, scope: Option[DependencyScope]): dependency[library] =
    new dependency(library)  {
      scope.foreach(ProjectStructureDsl.scope := _)
    }

  def libraries2libraryDependencies(libraries: Seq[library], scope: Option[DependencyScope]): Seq[dependency[library]] =
    libraries.map(library2libraryDependency(_, scope))
}

