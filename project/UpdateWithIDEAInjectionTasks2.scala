import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.jetbrains.sbtidea.Keys.{intellijAttachSources, intellijBaseDirectory, productInfo}
import org.jetbrains.sbtidea.download.idea.IdeaSourcesInstaller
import sbt.Keys.*
import sbt.{Def, File, *}

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

/**
 * The code inside is very similar to [[org.jetbrains.sbtidea.tasks.UpdateWithIDEAInjectionTask]]
 * Consider deduplicating it, extracting common utilites in sbt-idea-plugin
 */
object UpdateWithIDEAInjectionTasks2 {

  def getUpdateReportWithIntellijSdkSubsetModuleTask(
    intellijSdkSubsetInfo: IntellijSdkSubsetInfo
  ): Def.Initialize[Task[UpdateReport]] = Def.task {
    val intellijBaseDir = intellijBaseDirectory.value

    val jars = intellijSdkSubsetInfo.jarsRelativePaths.map(intellijBaseDir / _)

    val updateReportOriginal = update.value
    val buildNumber = productInfo.value.buildNumber
    val attachSources = intellijAttachSources.in(Global).value

    injectIntellijSdkSubsetModule(
      reportOriginal = updateReportOriginal,
      jars = jars,
      buildNumber = buildNumber,
      intellijBaseDir = intellijBaseDir,
      attachSources = attachSources,
      artifact = intellijSdkSubsetInfo.artifact,
      module = intellijSdkSubsetInfo.modulePrefix % buildNumber,
    )
  }

  private def injectIntellijSdkSubsetModule(
    reportOriginal: UpdateReport,
    jars: Seq[File],
    buildNumber: String,
    intellijBaseDir: File,
    attachSources: Boolean,
    artifact: Artifact,
    module: ModuleID,
  ): UpdateReport = {
    val ijSourcesArchives = findSourcesArchives(intellijBaseDir)

    val ijSourcesMappings: Seq[(Artifact, File)] =
      if (attachSources) ijSourcesArchives.map { file =>
        val extension = FilenameUtils.getExtension(file.getName)
        //similar to org.jetbrains.sbtidea.tasks.classpath.AttributedClasspathTasks.Artifacts$.ideaSourcesArtifact
        val sourcesArtifact = Artifact(name = artifact.name, `type` = Artifact.SourceType, extension = extension)
        sourcesArtifact -> file
      }
      else Seq.empty

    val intellijSubsetArtifactMappings: Seq[(Artifact, File)] =
      jars.map(artifact -> _) ++ ijSourcesMappings

    val injectInfos: Seq[(sbt.ModuleID, Seq[(sbt.Artifact, sbt.File)], Configuration)] = Seq(
      (module, intellijSubsetArtifactMappings, Configurations.Compile),
    )

    val reportNew = injectInfos.foldLeft(reportOriginal) { case (previousReport, (module, artifactMappings, configuration)) =>
      injectModulesIntoUpdateReport(previousReport, module, artifactMappings, configuration)
    }
    reportNew //keep a separate variable for easier debugging
  }

  private def findSourcesArchives(intellijBaseDir: File): Seq[File] = {
    val sourcesDir = IdeaSourcesInstaller.sourcesRoot(intellijBaseDir.toPath).toFile
    if (sourcesDir.isDirectory) FileUtils.listFiles(sourcesDir, Array("zip", "jar"), false).asScala.toSeq
    else Seq()
  }

  private def injectModulesIntoUpdateReport(
    report: UpdateReport,
    module: ModuleID,
    artifacts: Seq[(Artifact, File)],
    configuration: Configuration,
  ): UpdateReport = {
    val newConfigurationReports = report.configurations.map { confReport =>
      if (confReport.configuration.name == configuration.name) {
        val newModuleReport = ModuleReport(module, artifacts.toVector, Vector.empty)
        val modules = confReport.modules :+ newModuleReport
        confReport.withModules(modules)
      } else
        confReport
    }
    report.withConfigurations(newConfigurationReports)
  }
}