package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.{module => OpenapiModule}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.packagesearch.ui.AddDependencyPreviewWizard
import org.jetbrains.plugins.scala.packagesearch.utils.SbtCommon.refreshSbtProject
import org.jetbrains.plugins.scala.packagesearch.utils.SbtDependencyUtils.GetMode.{GetDep, GetPlace}
import org.jetbrains.plugins.scala.packagesearch.utils.SbtDependencyUtils.getSbtFileOpt
import org.jetbrains.plugins.scala.packagesearch.utils.{ArtifactInfo, SbtCommon, SbtDependencyTraverser, SbtDependencyUtils}
import org.jetbrains.sbt.annotator.dependency.DependencyPlaceInfo
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.ModuleExtData
import org.jetbrains.sbt.SbtUtil

import java.util
import scala.jdk.CollectionConverters._

class SbtDependencyModifier extends ExternalDependencyModificator{

  override def supports(module: OpenapiModule.Module): Boolean = SbtUtil.isSbtModule(module)

  override def addDependency(module: OpenapiModule.Module, newDependency: UnifiedDependency): Unit = try {
    implicit val project: Project = module.getProject
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return
    val dependencyPlaces = SbtDependencyUtils.getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, GetPlace).map(
      psiAndString => inReadAction(SbtDependencyUtils.toDependencyPlaceInfo(psiAndString._1, Seq()))
    ).map {
      case Some(inside: DependencyPlaceInfo) => inside
      case _ => null
    }.filter(_ != null).sortWith(_.toString < _.toString)
    val newDependencyCoordinates = newDependency.getCoordinates
    val newArtifactInfo = ArtifactInfo(
      newDependencyCoordinates.getGroupId,
      newDependencyCoordinates.getArtifactId,
      newDependencyCoordinates.getVersion,
      newDependency.getScope)

    ApplicationManager.getApplication.invokeLater { () =>
      val wizard = new AddDependencyPreviewWizard(
        project,
        Some(newArtifactInfo),
        dependencyPlaces)
      wizard.search() match {
        case Some(fileLine) =>
          SbtDependencyUtils.addDependency(fileLine.element, newArtifactInfo)(project)
//          refreshSbtProject(project)
        case _ =>
      }
    }
  } catch {
    case e: Exception => throw(e)
  }

  override def updateDependency(module: OpenapiModule.Module, currentDependency: UnifiedDependency, newDependency: UnifiedDependency): Unit = {
    implicit val project: Project = module.getProject
    val targetedLibDep = SbtDependencyUtils.findLibraryDependency(project, module, currentDependency)
    if (targetedLibDep == null) return
    val newCoordinates = newDependency.getCoordinates
    val newDepText: String = SbtDependencyUtils.generateArtifactTextVerbose(
      newCoordinates.getGroupId,
      newCoordinates.getArtifactId,
      newCoordinates.getVersion,
      newDependency.getScope)
    inWriteCommandAction(targetedLibDep.replace(code"$newDepText"))(project)
//    }
  }

  override def removeDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency): Unit = {

  }

  override def addRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }

  override def deleteRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }



  override def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = try {
    
    // Check whether the IDE is in Dumb Mode. If it is, return empty list instead proceeding
    if (DumbService.getInstance(module.getProject).isDumb) return List().asJava
    
    val libDeps = SbtDependencyUtils.
      getLibraryDependenciesOrPlaces(getSbtFileOpt(module), module.getProject, module, GetDep).
      map(_.asInstanceOf[(ScInfixExpr, String)])


    implicit val project: Project = module.getProject
    var scalaVer: String = ""
    val moduleExtData = SbtUtil.getModuleData(
      project,
      ExternalSystemApiUtil.getExternalProjectId(module),
      ModuleExtData.Key).toSeq
    if (moduleExtData.nonEmpty) scalaVer = moduleExtData.head.scalaVersion

    inReadAction({
      libDeps.map(libDepInfixAndString => {
        val libDepArr = SbtDependencyUtils.postProcessDependency(SbtDependencyUtils.getTextFromInfixAndString(libDepInfixAndString))
        val dataContext = new DataContext {
          override def getData(dataId: String): AnyRef = {
            if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
              return libDepInfixAndString
            }
            null
          }
        }
        libDepArr.length match {
          case x if x < 3 || x > 4 => null
          case x if x == 3 => new DeclaredDependency(
            new UnifiedDependency(
              libDepArr(0),
              SbtDependencyUtils.buildScalaDependencyString(libDepArr(1), scalaVer),
              libDepArr(2),
              SbtCommon.defaultLibScope),
            dataContext)
          case x if x == 4 => new DeclaredDependency(
            new UnifiedDependency(
              libDepArr(0),
              SbtDependencyUtils.buildScalaDependencyString(libDepArr(1), scalaVer),
              libDepArr(2),
              libDepArr(3)),
            dataContext)
        }
      }).filter(_ != null).toList.asJava
    })
  } catch {
    case e: Exception =>
      throw e
  }

  override def declaredRepositories(module: OpenapiModule.Module): util.List[UnifiedDependencyRepository] = {
    List().asJava
  }
}
