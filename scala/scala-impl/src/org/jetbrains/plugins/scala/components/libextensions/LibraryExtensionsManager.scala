package org.jetbrains.plugins.scala.components.libextensions

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, IvyResolver, MavenResolver, ResolvedDependency}
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
import org.jetbrains.plugins.scala.components.libextensions.api.psi._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.immutable

class LibraryExtensionsManager(project: Project) extends AbstractProjectComponent(project) {
  import LibraryExtensionsManager._

  def searchExtensions(sbtResolvers: Set[SbtResolver]): Unit = {
    val allLibraies = ProjectLibraryTable.getInstance(project).getLibraries
    val ivyResolvers = sbtResolvers.toSeq.map {
      case r: SbtMavenResolver => MavenResolver(r.name, r.root)
      case r: SbtIvyResolver   => IvyResolver(r.name, r.root)
    }
    val candidates = getExtensionLibCandidates(allLibraies)
    val resolved = new IvyExtensionsResolver(ivyResolvers).resolve(candidates.toSeq:_*)
    processResolvedExtensions(resolved)
  }

  private def getExtensionLibCandidates(libs: Seq[Library]): Set[DependencyDescription] = {
    val patterns = ScalaProjectSettings.getInstance(project).getLextSearchPatterns.asScala
    def processLibrary(lib: Library): Seq[DependencyDescription] = lib.getName.split(": ?") match {
      case Array("sbt", org, module, version, "jar") =>
        val subst = patterns.map(_.replace(PAT_ORG, org).replace(PAT_MOD, module).replace(PAT_VER, version))
        subst.map(_.split(s" *$PAT_SEP *")).collect {
          case Array(newOrg,newMod,newVer) => DependencyDescription(newOrg, newMod, newVer)
        }.toSeq
      case _ => Seq.empty
    }
    var resultSet = immutable.HashSet[DependencyDescription]()
    libs.foreach(resultSet ++= processLibrary(_))
    resultSet
  }

  def processResolvedExtensions(resolvedList: Seq[ResolvedDependency]): Unit = {

  }

  def getAvailableLibraries: Seq[LibraryDescriptor] = Seq(
    LibraryDescriptor(
      "shapeless", "", "extensions for shapeless", "org.jetbrains", "3.2.3",
      PluginDescriptor(Version.Snapshot, Version.Snapshot, None,
        inspections = Extension("org.foo.Bar", "Cool inspection", enabled = true):: Nil)
        :: Nil
    )
  )

}

object LibraryExtensionsManager {

  val PAT_ORG = "$ORG"
  val PAT_MOD = "$MOD"
  val PAT_VER = "$VER"
  val PAT_SEP = "%"

  val DEFAULT_PATTERN = s"$PAT_ORG $PAT_SEP $PAT_MOD-ijextensions $PAT_SEP $PAT_VER"

  case class DeclaredExtension(xmlKind: String, apiClass: Class[_])

  val availableExtensions: List[DeclaredExtension] = DeclaredExtension("type-transformer", classOf[MacroTypeContributor]) ::
                            DeclaredExtension("expression-transformer", classOf[MacroExprContributor]) ::
                            DeclaredExtension("tmplatedef-transformer", classOf[TypeDefTransformer]) ::
                            DeclaredExtension("synthetic-member-injector", classOf[SyntheticMembersInjector]) ::
                            DeclaredExtension("inspection", classOf[Inspection]) ::
                            DeclaredExtension("intention", classOf[Intention]) ::
                            DeclaredExtension("migrator", classOf[Migrator]) :: Nil

  def getInstance(project: Project): LibraryExtensionsManager = project.getComponent(classOf[LibraryExtensionsManager])

}
