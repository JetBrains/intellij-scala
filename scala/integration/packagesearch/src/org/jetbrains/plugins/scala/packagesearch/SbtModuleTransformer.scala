package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.NavigatableAdapter
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{AsyncModuleTransformer, BuildSystemType, DependencyDeclarationIndexes, PackageSearchModule}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.packagesearch.utils.{SbtProjectModuleType, ScalaKotlinHelper}
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.language.utils.{SbtDependencyCommon, SbtDependencyUtils}

import java.io.File
import java.util
import java.util.Collections.emptyList
import java.util.concurrent.CompletableFuture
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

//noinspection UnstableApiUsage,ApiStatus,ScalaDeprecation
class SbtModuleTransformer(private val project: Project) extends AsyncModuleTransformer {
  private val logger = Logger.getInstance(this.getClass)

  //TODO: delete unused before next release
  def findModulePaths(module: Module): Array[File] = {
    if (!SbtUtil.isSbtModule(module)) return null
    val contentRoots = ModuleRootManager.getInstance(module).getContentRoots
    if (contentRoots.length < 1) return null
    contentRoots.map(virtualFile => {
      new File(virtualFile.getPath)
    })
  }

  private def createNavigableDependencyCallback(
    project: Project,
    module: Module,
  ): DeclaredDependency => CompletableFuture[DependencyDeclarationIndexes] =
    (dependency: DeclaredDependency) => {
      val groupId = dependency.getCoordinates.getGroupId
      val artifactId = dependency.getCoordinates.getArtifactId
      val version = dependency.getCoordinates.getVersion

      val targetedLibraryDependency: (ScInfixExpr, String, ScInfixExpr) = DumbService.getInstance(project).runReadActionInSmartMode { () =>
        SbtDependencyUtils.findLibraryDependency(
          project,
          module,
          new UnifiedDependency(groupId, artifactId, version, SbtDependencyCommon.defaultLibScope),
          configurationRequired = false
        )
      }

      new NavigatableAdapter() {
        override def navigate(requestFocus: Boolean): Unit = {
          PsiNavigationSupport.getInstance.createNavigatable(
            project,
            targetedLibraryDependency._1.getContainingFile.getVirtualFile,
            targetedLibraryDependency._1.getTextOffset
          ).navigate(requestFocus)
        }
      }

      //TODO: pass proper indexes after this is fixed: SCL-19838
      val dependencyIndexes = new DependencyDeclarationIndexes(0, 0, 0)
      CompletableFuture.completedFuture(dependencyIndexes)
    }

  private def obtainProjectModulesFor(module: Module, dumbMode: Boolean): Option[PackageSearchModule] = try {
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    sbtFileOpt match {
      case Some(buildFile: VirtualFile) =>
        val projectModule = new PackageSearchModule(
          module.getName,
          module,
          null,
          buildFile,
          buildFile.getParent.toNioPath.toFile,
          SbtModuleTransformer.buildSystemType,
          SbtProjectModuleType,
          emptyList(),
          (_: DeclaredDependency) => CompletableFuture.completedFuture(null)
        )
        Some {
          if (!dumbMode) {
            val callback = createNavigableDependencyCallback(project, module)
            ScalaKotlinHelper.createNavigatableProjectModule(projectModule, callback)
          }
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

  override def transformModules(project: Project, nativeModules: util.List[_ <: Module]): CompletableFuture[util.List[PackageSearchModule]] = {
    val dumbMode = DumbService.isDumb(project)

    val futuresBuffer = ListBuffer.empty[CompletableFuture[Option[PackageSearchModule]]]
    nativeModules.forEach { m =>
      val acceptable = SbtUtil.isSbtModule(m) &&
        SbtUtil.getBuildModuleData(project, m).isDefined

      if (acceptable) {
        futuresBuffer += CompletableFuture.supplyAsync(() => obtainProjectModulesFor(m, dumbMode))
      }
    }
    val futures = futuresBuffer.result()
    CompletableFuture.allOf(futures: _*)
      .thenApply[util.List[PackageSearchModule]](_ => futures.flatMap(_.join()).distinct.asJava)
  }
}

private object SbtModuleTransformer {

  //noinspection UnstableApiUsage
  private[packagesearch] val buildSystemType: BuildSystemType =
    new BuildSystemType("SBT", "scala", null)
}
