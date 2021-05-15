package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.{module => OpenapiModule}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.packagesearch.ui.AddDependencyPreviewWizard
import org.jetbrains.plugins.scala.packagesearch.utils.SbtDependencyUtils.GetMode.{GetDep, GetPlace}
import org.jetbrains.plugins.scala.packagesearch.utils.SbtDependencyUtils.getSbtFileOpt
import org.jetbrains.plugins.scala.packagesearch.utils.{SbtCommon, SbtDependencyTraverser, SbtDependencyUtils}
import org.jetbrains.sbt.annotator.dependency.DependencyPlaceInfo
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.ModuleExtData
import org.jetbrains.sbt.SbtUtil

import java.util
import scala.jdk.CollectionConverters._

class SbtDependencyModifier extends ExternalDependencyModificator{

  override def supports(module: OpenapiModule.Module): Boolean = SbtUtil.isSbtModule(module)

  def refresh(project: Project): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }

  override def addDependency(module: OpenapiModule.Module, newDependency: UnifiedDependency): Unit = try {
    implicit val project: Project = module.getProject
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return
    val dependencyPlaces = SbtDependencyUtils.getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, GetPlace).map {
      case depPlace: DependencyPlaceInfo => Some(depPlace)
      case other => inReadAction(SbtDependencyUtils.toDependencyPlaceInfo(other.asInstanceOf[PsiElement], Seq()))
    }.map {
      case Some(inside: DependencyPlaceInfo) => inside
//      case outside: DependencyPlaceInfo => outside
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
          refresh(project)
        case _ =>
      }
    }
  } catch {
    case e: Exception => throw(e)
  }

  override def updateDependency(module: OpenapiModule.Module, currentDependency: UnifiedDependency, newDependency: UnifiedDependency): Unit = {
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    val currentCoordinates = currentDependency.getCoordinates
    val currentDepText: String = SbtDependencyUtils.generateArtifactTextVerbose(currentCoordinates.getGroupId, currentCoordinates.getArtifactId, currentCoordinates.getVersion, currentDependency.getScope)
    val newCoordinates = newDependency.getCoordinates
    implicit val project: Project = module.getProject
    val libDeps = SbtDependencyUtils.getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, GetDep)
    libDeps.foreach(
      libDep => {
        var processedDep: Array[String] = Array()
        processedDep = SbtDependencyUtils.postProcessDependency(SbtDependencyUtils.getTextFromInfix(libDep.asInstanceOf[ScInfixExpr]))
        var processedDepText: String = ""
        processedDep match {
          case Array(a,b,c) => processedDepText = SbtDependencyUtils.generateArtifactTextVerbose(a, b, c, SbtCommon.defaultLibScope)
          case Array(a,b,c,d) => processedDepText = SbtDependencyUtils.generateArtifactTextVerbose(a, b, c, d)
          case _ =>
        }

        if (currentDepText.equals(processedDepText)) {
          libDep.asInstanceOf[ScInfixExpr].left match {
            case refExpr: ScReferenceExpression =>
              var moduleId: Seq[ScInfixExpr] = Seq.empty
              def callback(psiElement: PsiElement):Unit = {
                psiElement match {
                  case infixExpr: ScInfixExpr if infixExpr.operation.refName.contains("%") => moduleId ++= Seq(infixExpr)
                  case _ =>
                }
              }
              SbtDependencyTraverser.traverseReferenceExpr(refExpr)(callback)
              val newDepText: String = SbtDependencyUtils.generateArtifactTextVerbose(
                newCoordinates.getGroupId,
                newCoordinates.getArtifactId,
                newCoordinates.getVersion,
                SbtCommon.defaultLibScope)

              inWriteCommandAction({
                moduleId.head.replace(code"$newDepText")
                var highLevelChange = s"${libDep.asInstanceOf[ScInfixExpr].left.getText}"
                if (newDependency.getScope != SbtCommon.defaultLibScope)
                  highLevelChange += s" % ${newDependency.getScope}"

                libDep.asInstanceOf[ScInfixExpr].replace(code"${highLevelChange}")
              })(project)
            case _ =>
              val newDepText: String = SbtDependencyUtils.generateArtifactTextVerbose(
                newCoordinates.getGroupId,
                newCoordinates.getArtifactId,
                newCoordinates.getVersion,
                newDependency.getScope)
              inWriteCommandAction(libDep.asInstanceOf[ScInfixExpr].replace(code"$newDepText"))(project)
          }

        }
      }
    )
  }

  override def removeDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency): Unit = {

  }

  override def addRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }

  override def deleteRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }



  override def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = {
    val libDeps = SbtDependencyUtils.getLibraryDependenciesOrPlaces(getSbtFileOpt(module), module.getProject, module, GetDep)

    val dataContext = new DataContext {
      override def getData(dataId: String): AnyRef = null
    }
    implicit val project: Project = module.getProject
    var scalaVer: String = ""
    val moduleExtData = SbtUtil.getModuleData(
      project,
      ExternalSystemApiUtil.getExternalProjectId(module),
      ModuleExtData.Key).toSeq
    if (moduleExtData.nonEmpty) scalaVer = moduleExtData.head.scalaVersion

    inReadAction({
      libDeps.map(libDepInfix => {
        val libDepArr = SbtDependencyUtils.postProcessDependency(SbtDependencyUtils.getTextFromInfix(libDepInfix.asInstanceOf[ScInfixExpr]))
        libDepArr.length match {
          case x if x < 3 || x > 4 => null
          case x if x == 3 => new DeclaredDependency(
            new UnifiedDependency(
              libDepArr(0),
              SbtCommon.buildScalaDependencyString(libDepArr(1), scalaVer),
              libDepArr(2),
              SbtCommon.defaultLibScope),
            dataContext)
          case x if x == 4 => new DeclaredDependency(
            new UnifiedDependency(
              libDepArr(0),
              SbtCommon.buildScalaDependencyString(libDepArr(1), scalaVer),
              libDepArr(2),
              libDepArr(3)),
            dataContext)
        }
      }).filter(_ != null).toList.asJava
    })
  }

  override def declaredRepositories(module: OpenapiModule.Module): util.List[UnifiedDependencyRepository] = {
    List().asJava
  }
}
