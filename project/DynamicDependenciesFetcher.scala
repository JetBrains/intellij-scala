import coursier.core.{Dependency, ModuleName}
import coursier.{Classifier, Fetch, Module, Organization}
import sbt.{Def, *}

import java.nio.file.Path

/**
 * Fetches artifacts from Maven which are not project dependencies, instead are resolved dynamically by the plugin at
 * runtime. By avoiding to specify these artifacts as library dependencies, we can resolve the same artifact for
 * different versions of Scala in one place, without having to work around sbt eviction strategies.
 *
 * https://github.com/JetBrains/sbt-idea-plugin/issues/110
 */
object DynamicDependenciesFetcher extends AutoPlugin {

  val dynamicDependencies: SettingKey[Seq[(DynamicDependency, String)]] = settingKey("dynamic dependencies to be fetched")
  val dynamicDependenciesUpdate: TaskKey[Seq[(Path, Path)]] = taskKey("resolve and fetch runtime dependencies")

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    dynamicDependenciesUpdate := fetchDynamicDependencies(dynamicDependencies.value)
  )

  def binaryDep(organization: String, name: String, version: String): DynamicDependency.Binary =
    DynamicDependency.Binary(dep(organization, name, version))

  def binaryDep(organization: String, name: String, scalaVersion: String, version: String): DynamicDependency.Binary =
    binaryDep(organization, s"${name}_$scalaVersion", version)

  def sourceDep(organization: String, name: String, scalaVersion: String, version: String): DynamicDependency.Source =
    DynamicDependency.Source(dep(organization, s"${name}_$scalaVersion", version))

  private def dep(organization: String, name: String, version: String): Dependency =
    Dependency(Module(Organization(organization), ModuleName(name)), version)

  private def fetchDynamicDependencies(dependencies: Seq[(DynamicDependency, String)]): Seq[(Path, Path)] =
    dependencies.map { case (dep, target) => fetchDynamicDependency(dep) -> Path.of(target) }

  private def fetchDynamicDependency(dependency: DynamicDependency): Path = {
    val fetch = dependency match {
      case DynamicDependency.Binary(dep) => Fetch().withDependencies(Seq(dep)).noExtraArtifacts()
      case DynamicDependency.Source(dep) => Fetch().withDependencies(Seq(dep)).withClassifiers(Set(Classifier.sources))
    }
    val fetched = fetch.runResult().detailedArtifacts
    fetched.find(_._1.moduleVersion == dependency.dep.moduleVersion).map(_._4).get.toPath
  }

  sealed trait DynamicDependency {
    def dep: Dependency
  }

  object DynamicDependency {
    case class Binary(dep: Dependency) extends DynamicDependency
    case class Source(dep: Dependency) extends DynamicDependency
  }
}
