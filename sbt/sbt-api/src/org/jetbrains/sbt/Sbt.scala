package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.buildinfo.BuildInfo
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.sbt.project.SbtProjectSystem

import java.net.URI
import java.util.Objects
import javax.swing.Icon

object Sbt {
  @NonNls val Name = "sbt"

  @NonNls val Extension = ".sbt"

  @NonNls val BuildFile = "build.sbt"

  @NonNls val PropertiesFile = "build.properties"

  @NonNls val ProjectDirectory = "project"

  @NonNls val PluginsFile = "plugins.sbt"

  @NonNls val TargetDirectory = "target"

  @NonNls val ModulesDirectory = ".idea/modules"

  @NonNls val BuildModuleSuffix = "-build"

  @NonNls val BuildLibraryPrefix = "sbt-"

  @NonNls val UnmanagedLibraryName = "unmanaged-jars"

  @NonNls val UnmanagedSourcesAndDocsName = "unmanaged-sources-and-docs"

  @NonNls val DefinitionHolderClasses: Seq[String] = Seq("sbt.Plugin", "sbt.Build")

  // this should be in sync with sbt.BuildUtil.baseImports
  @NonNls val DefaultImplicitImports: Seq[String] = Seq("sbt._", "Process._", "Keys._", "dsl._")

  val LatestVersion: Version = Version(BuildInfo.sbtLatestVersion)
  val Latest_1_0: Version = Version(BuildInfo.sbtLatest_1_0)
  val Latest_0_13: Version = Version(BuildInfo.sbtLatest_0_13)

  /**
   * '''ATTENTION!'''<br>
   * Don't do these icons `val`. They are initialized  in test suites fields (e.g. via Sbt.LatestVersion)
   * This can lead to initialization of [[com.github.benmanes.caffeine.cache.Caffeine]] which under the hood
   * initializes ForkJoinPool via `getExecutor` method call. This initialization is done before test initialization
   * which leads to ignoring of [[com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory]].
   *
   * '''Note!''' <br>
   * When tests are run using sbt with filtering of category (using `--exclude-categories=`), all the filtered
   * tests are actually initialized (only constrictor). So if some ignored test contains a field with a reference
   * to Icon, it will lead to corrupted ForkJoinPool in non-ignored tests.
   *
   * @see [[com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory]]
   * @see `com.intellij.concurrency.PoisonFactory`
   */
  def Icon: Icon = Icons.SBT
  def FolderIcon: Icon = Icons.SBT_FOLDER
}
import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}

object OO {
  def datakey[T](clazz: Class[T],
                 weight: Int = ProjectKeys.MODULE.getProcessingWeight + 1
                ): Key[T] = new Key(clazz.getName, weight)

}


abstract class SbtEntityData extends AbstractExternalEntityData(SbtProjectSystem.Id) with Product {

  // need to manually specify equals/hashCode here because it is not generated for case classes inheriting from
  // AbstractExternalEntityData
  override def equals(obj: scala.Any): Boolean = obj match {
    case data: SbtEntityData =>
      //noinspection CorrespondsUnsorted
      this.canEqual(data) &&
        (this.productIterator sameElements data.productIterator)
    case _ => false
  }

  override def hashCode(): Int = runtime.ScalaRunTime._hashCode(this)

}

@SerialVersionUID(2)
final class MyURI @PropertyMapping(Array("string"))(
  private val string: String
) extends Serializable {
  assert(string != null)

  @transient val uri: URI = new URI(string)

  def this(uri: URI) = {
    this(uri.toString)
  }

  override def toString: String = Objects.toString(uri)

  override def hashCode(): Int = Objects.hashCode(uri)

  override def equals(obj: Any): Boolean = obj match {
    case other: MyURI => uri == other.uri
    case _ => false
  }
}

/** Data describing a project which is part of an sbt build. */
@SerialVersionUID(3)
case class SbtModuleData @PropertyMapping(Array("id", "buildURI", "isSourceModule")) (
  id: String,
  @Nullable buildURI: MyURI,
  isSourceModule: Boolean
) extends SbtEntityData

object SbtModuleData {
  val Key: Key[SbtModuleData] = OO.datakey(classOf[SbtModuleData])

  def apply(id: String, buildURI: Option[URI], isSourceModule: Boolean): SbtModuleData =
    new SbtModuleData(id, buildURI.map(new MyURI(_)).orNull, isSourceModule)
}
