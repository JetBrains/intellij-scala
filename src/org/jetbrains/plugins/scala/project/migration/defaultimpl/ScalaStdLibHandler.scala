package org.jetbrains.plugins.scala.project.migration.defaultimpl

import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.projectRoots.{JavaSdkVersion, JdkVersionUtil}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.SAM.ConvertExpressionToSAMInspection
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.migration.ScalaLibraryMigrator
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, MigrationLocalFixHolder, MigrationReport}
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationLocalFixHolderImpl
import org.jetbrains.plugins.scala.project.migration.handlers.VersionedArtifactHandlerBase
import org.jetbrains.plugins.scala.project.template.Artifact.ScalaLibrary

/**
  * User: Dmitry.Naydanov
  * Date: 06.09.16.
  */
class ScalaStdLibHandler extends VersionedArtifactHandlerBase(ScalaLibrary, Seq(Version("2.11")), Seq(Version("2.12")), true) {
  override def getMigrators(from: Library, to: LibraryData): Iterable[ScalaLibraryMigrator] = Seq(new StdLib211to212)
  
  
  private class StdLib211to212 extends ScalaLibraryMigrator {
    private val samInspection = new ConvertExpressionToSAMInspection
    
    override def migrateGlobal(projectStructure: MigrationApiService): Option[MigrationReport] = {
      val project = projectStructure.getProject
      val sdksModel = new ProjectSdksModel
      
      projectStructure.onEdt {
        sdksModel.reset(project)
        val sdks = sdksModel.getSdks

        sdks.find(sdk => JdkVersionUtil.getVersion(sdk.getVersionString) == JavaSdkVersion.JDK_1_8) match {
          case Some(jdk) =>
            if(sdksModel.getProjectSdk != jdk) projectStructure.inWriteAction(ProjectRootManager.getInstance(project).setProjectSdk(jdk))
          case None => MigrationReport.createSingleMessageReport(MigrationReport.Warning, "No JDK 1.8 found. Scala 2.12 supports Java 1.8 only")
        }
      }
      
      None
    }

    override def migrateLocal(file: PsiFile, localFixHolder: MigrationLocalFixHolder): Option[PartialFunction[PsiElement, Any]] = {
      file match {
        case scalaFile: ScalaFile if !scalaFile.isCompiled && !scalaFile.isWorksheetFile =>
          Some(samInspection actionFor localFixHolder.asInstanceOf[MigrationLocalFixHolderImpl].getHolder)
        case _ => None
      }
    }

    override def getName: String = "Std lib migrator"

    override def getDescription: String = "Azaza"
  }
}
