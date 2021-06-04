package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.{module => OpenapiModule}
import com.intellij.psi.PsiManager
import org.jetbrains.idea.maven.indices.MavenIndex
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.packagesearch.ui.AddDependencyOrRepositoryPreviewWizard
import org.jetbrains.sbt.language.utils.SbtCommon.defaultLibScope
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.{GetDep, GetPlace}
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.getSbtFileOpt
import org.jetbrains.sbt.project.data.ModuleExtData
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.language.utils.{ArtifactInfo, DependencyOrRepositoryPlaceInfo, SbtCommon, SbtDependencyUtils}
import org.jetbrains.sbt.resolvers.{SbtMavenResolver, SbtResolverUtils}

import java.util
import scala.jdk.CollectionConverters._

class SbtDependencyModifier extends ExternalDependencyModificator{

  override def supports(module: OpenapiModule.Module): Boolean = SbtUtil.isSbtModule(module)

  override def addDependency(module: OpenapiModule.Module, newDependency: UnifiedDependency): Unit = try {
    implicit val project: Project = module.getProject
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return
    val dependencyPlaces = inReadAction ( for {
      sbtFile <- sbtFileOpt
      psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
      sbtFileModule = psiSbtFile.module.orNull
      topLevelPlace = if (sbtFileModule != null && (sbtFileModule == module || sbtFileModule.getName == s"""${module.getName}-build"""))
        Seq(SbtDependencyUtils.getTopLevelPlaceToAdd(psiSbtFile))
      else Seq.empty

      depPlaces = (SbtDependencyUtils.getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, GetPlace).map(
        psiAndString => SbtDependencyUtils.toDependencyPlaceInfo(psiAndString._1, Seq()))
        ++ topLevelPlace)
        .map {
        case Some(inside: DependencyOrRepositoryPlaceInfo) => inside
        case _ => null
      }.filter(_ != null).sortWith(_.toString < _.toString)
    } yield depPlaces).getOrElse(Seq.empty)
    val newDependencyCoordinates = newDependency.getCoordinates
    val newArtifactInfo = ArtifactInfo(
      newDependencyCoordinates.getGroupId,
      newDependencyCoordinates.getArtifactId,
      newDependencyCoordinates.getVersion,
      newDependency.getScope)

    ApplicationManager.getApplication.invokeLater { () =>
      val wizard = new AddDependencyOrRepositoryPreviewWizard(
        project,
        newArtifactInfo,
        dependencyPlaces)
      wizard.search() match {
        case Some(fileLine) =>
          SbtDependencyUtils.addDependency(fileLine.element, newArtifactInfo)(project)
        case _ =>
      }
    }
  } catch {
    case e: Exception => throw(e)
  }

  override def updateDependency(module: OpenapiModule.Module, currentDependency: UnifiedDependency, newDependency: UnifiedDependency): Unit = try {
    implicit val project: Project = module.getProject
    val targetedLibDepTuple = SbtDependencyUtils.findLibraryDependency(project, module, currentDependency)
    if (targetedLibDepTuple == null) return
    val oldLibDep = SbtDependencyUtils.processLibraryDependencyFromExprAndString(targetedLibDepTuple, preserve = true)
    val newCoordinates = newDependency.getCoordinates

    if (SbtDependencyUtils.cleanUpDependencyPart(oldLibDep(2).asInstanceOf[ScStringLiteral].getText) != newCoordinates.getVersion) {
      inWriteCommandAction(oldLibDep(2).asInstanceOf[ScStringLiteral].replace(ScalaPsiElementFactory.createElementFromText(s""""${newCoordinates.getVersion}"""")))
      return
    }
    var oldConfiguration = ""
    if (targetedLibDepTuple._2 != "") oldConfiguration = SbtDependencyUtils.cleanUpDependencyPart(targetedLibDepTuple._2)

    if (oldLibDep.length > 3) oldConfiguration = SbtDependencyUtils.cleanUpDependencyPart(oldLibDep(3).asInstanceOf[String])
    val newConfiguration = if (newDependency.getScope != defaultLibScope) newDependency.getScope else ""
    if (oldConfiguration.toLowerCase != newConfiguration.toLowerCase) {
      if (targetedLibDepTuple._2 != "") {
        if (newConfiguration == "") {
          inWriteCommandAction(targetedLibDepTuple._3.replace(code"${targetedLibDepTuple._3.left.getText}"))
        }
        else {
          inWriteCommandAction(targetedLibDepTuple._3.right.replace(code"${newConfiguration}"))
        }

      }
      else {
        if (oldLibDep.length > 3) {
          if (newConfiguration == "") {
            inWriteCommandAction(targetedLibDepTuple._1.replace(code"${targetedLibDepTuple._1.left}"))
          }
          else {
            inWriteCommandAction(targetedLibDepTuple._1.right.replace(code"""${newConfiguration}"""))
          }
        }
        else {
          if (newConfiguration != "") {
            inWriteCommandAction(targetedLibDepTuple._1.replace(code"""${targetedLibDepTuple._1.getText} % $newConfiguration"""))
          }
        }
      }
    }
  } catch {
    case e: Exception =>
      throw e
  }

  override def removeDependency(module: OpenapiModule.Module, toRemoveDependency: UnifiedDependency): Unit = try {
    implicit val project: Project = module.getProject
    val targetedLibDepTuple = SbtDependencyUtils.findLibraryDependency(project, module, toRemoveDependency)
    targetedLibDepTuple._3.getParent match {
      case _: ScArgumentExprList => inWriteCommandAction {
        targetedLibDepTuple._3.delete()
      }
      case infix: ScInfixExpr if infix.left.textMatches(SbtDependencyUtils.LIBRARY_DEPENDENCIES) => inWriteCommandAction {
        infix.delete()
      }
      case _ =>
    }
  } catch {
    case e: Exception =>
      throw e
  }

  override def addRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {
    implicit val project: Project = module.getProject
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return
    val sbtFile = sbtFileOpt.orNull
    if (sbtFile == null) return
    val psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]

    SbtDependencyUtils.addRepository(psiSbtFile, unifiedDependencyRepository)
  }

  override def deleteRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }



  override def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = try {

    // Check whether the IDE is in Dumb Mode. If it is, return empty list instead proceeding
    if (DumbService.getInstance(module.getProject).isDumb) return List().asJava

    val libDeps = SbtDependencyUtils.
      getLibraryDependenciesOrPlaces(getSbtFileOpt(module), module.getProject, module, GetDep).
      map(_.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)])


    implicit val project: Project = module.getProject
    var scalaVer: String = ""
    val moduleExtData = SbtUtil.getModuleData(
      project,
      ExternalSystemApiUtil.getExternalProjectId(module),
      ModuleExtData.Key).toSeq
    if (moduleExtData.nonEmpty) scalaVer = moduleExtData.head.scalaVersion

    inReadAction({
      libDeps.map(libDepInfixAndString => {
        val libDepArr = SbtDependencyUtils.processLibraryDependencyFromExprAndString(libDepInfixAndString).map(_.asInstanceOf[String])
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
    SbtResolverUtils.projectResolvers(module.getProject).collect {
      case r: SbtMavenResolver =>
        new UnifiedDependencyRepository(r.name, r.presentableName, MavenIndex.normalizePathOrUrl(r.root))
    }.toList.asJava
  }
}
