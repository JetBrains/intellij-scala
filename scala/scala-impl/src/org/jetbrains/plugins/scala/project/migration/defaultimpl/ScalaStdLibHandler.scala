package org.jetbrains.plugins.scala.project.migration.defaultimpl

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.SAM.ConvertExpressionToSAMInspection
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.migration.ScalaLibraryMigrator
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, MigrationReport}
import org.jetbrains.plugins.scala.project.migration.defaultimpl.ScalaStdLibHandler._
import org.jetbrains.plugins.scala.project.migration.handlers.VersionedArtifactHandlerBase
import org.jetbrains.plugins.scala.project.template.Artifact.ScalaLibrary

/**
  * User: Dmitry.Naydanov
  * Date: 06.09.16.
  */
class ScalaStdLibHandler extends VersionedArtifactHandlerBase(ScalaLibrary, Seq(Version_2_11), Seq(Version_2_12), true) {
  override def getMigrators(from: Library, to: LibraryData): Iterable[ScalaLibraryMigrator] = {
    if(extractVersion(from).exists(_ ~= Version_2_11) &&
      extractVersion(to).exists(_ ~= Version_2_12)) Seq(new StdLib211to212) else Seq.empty
  }


  private class StdLib211to212 extends ScalaLibraryMigrator {
    private val samInspection = new ConvertExpressionToSAMInspection

    override def migrateGlobal(projectStructure: MigrationApiService): Option[MigrationReport] = {
      val project = projectStructure.project
      val sdksModel = new ProjectSdksModel

      import JavaSdkVersion.fromVersionString

      extensions.invokeLater {
        sdksModel.reset(project)
        if (fromVersionString(sdksModel.getProjectSdk.getVersionString) == JavaSdkVersion.JDK_1_8) return None

        val sdks = sdksModel.getSdks

        sdks.find(sdk => fromVersionString(sdk.getVersionString) == JavaSdkVersion.JDK_1_8) match {
          case Some(jdk) =>
            if (sdksModel.getProjectSdk != jdk) inWriteAction(ProjectRootManager.getInstance(project).setProjectSdk(jdk))
          case None => MigrationReport.createSingleMessageReport(MigrationReport.Warning, "No JDK 1.8 found. Scala 2.12 supports Java 1.8 only")
        }
      }

      None
    }

    override def migrateLocal(file: PsiFile, holder: ProblemsHolder): Option[PartialFunction[PsiElement, Any]] = {
      file match {
        case scalaFile: ScalaFile => Some(samInspection.actionFor(holder))
        case _ => None
      }
    }

    override def getName: String = "Std lib migrator"

    override def getDescription: String = "Simple migrator for scala standard library (2.11 -> 2.12)"
  }
}

object ScalaStdLibHandler {
  val Version_2_11 = Version("2.11")
  val Version_2_12 = Version("2.12")
}
