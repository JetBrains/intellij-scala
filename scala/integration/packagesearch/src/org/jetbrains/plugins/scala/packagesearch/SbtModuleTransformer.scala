package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.{Navigatable, NavigatableAdapter}
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{AsyncModuleTransformer, ProjectModule}
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import org.jetbrains.plugins.scala.packagesearch.utils.{SbtProjectModuleType, ScalaKotlinHelper}
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.language.utils.{SbtDependencyCommon, SbtDependencyUtils}

import java.io.File
import java.util
import java.util.concurrent.CompletableFuture
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

class SbtModuleTransformer(private val project: Project) extends AsyncModuleTransformer {
  private val logger = Logger.getInstance(this.getClass)

  def findModulePaths(module: Module): Array[File] = {
    if (!SbtUtil.isSbtModule(module)) return null
    val contentRoots = ModuleRootManager.getInstance(module).getContentRoots
    if (contentRoots.length < 1) return null
    contentRoots.map(virtualFile => {
      new File(virtualFile.getPath)
    })
  }

  private def createNavigableDependencyCallback(project: Project,
                                        module: Module): (String, String, PackageVersion) =>
    Navigatable = (groupId: String, artifactId: String, packageVersion: PackageVersion) => {

    val targetedLibDep = DumbService.getInstance(project).runReadActionInSmartMode { () =>
      SbtDependencyUtils.findLibraryDependency(
        project,
        module,
        new UnifiedDependency(groupId, artifactId, packageVersion.toString, SbtDependencyCommon.defaultLibScope),
        configurationRequired = false
      )
    }

    new NavigatableAdapter() {
      override def navigate(requestFocus: Boolean): Unit = {
        PsiNavigationSupport.getInstance.createNavigatable(
          project,
          targetedLibDep._1.getContainingFile.getVirtualFile,
          targetedLibDep._1.getTextOffset
        ).navigate(requestFocus)
      }
    }
  }

  private def obtainProjectModulesFor(module: Module, dumbMode: Boolean): Option[ProjectModule] = try {
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    sbtFileOpt match {
      case Some(buildFile: VirtualFile) =>
          val projectModule = new ProjectModule(
            module.getName,
            module,
            null,
            buildFile,
            buildFile.getParent.toNioPath.toFile,
            PackageSearchSbtBundle.buildSystemType,
            SbtProjectModuleType
        )
        Some {
          if (!dumbMode)
            ScalaKotlinHelper.createNavigatableProjectModule(projectModule, createNavigableDependencyCallback(project, module))
          else
            projectModule
        }
      case _ => None
    }

  } catch {
    case c: ControlFlowException => throw c
    case e: Exception =>
      logger.error(s"Transforming ${module.getName}", e)
      None
  }

  override def transformModules(project: Project, nativeModules: util.List[_ <: Module]): CompletableFuture[util.List[ProjectModule]] = {
    val dumbMode = DumbService.isDumb(project)

    val futuresBuffer = ListBuffer.empty[CompletableFuture[Option[ProjectModule]]]
    nativeModules.forEach { m =>
      val acceptable = SbtUtil.isSbtModule(m) &&
        SbtUtil.getBuildModuleData(project, ExternalSystemApiUtil.getExternalProjectId(m)).isDefined

      if (acceptable) {
        futuresBuffer += CompletableFuture.supplyAsync(() => obtainProjectModulesFor(m, dumbMode))
      }
    }

    val futures = futuresBuffer.result()
    CompletableFuture.allOf(futures: _*)
      .thenApply[util.List[ProjectModule]](_ => futures.flatMap(_.join()).distinct.asJava)
  }
}
