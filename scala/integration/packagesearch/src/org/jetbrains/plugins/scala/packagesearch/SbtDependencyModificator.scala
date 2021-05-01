package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.openapi.{module => OpenapiModule}
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInfixExprImpl, ScReferenceExpressionImpl}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtUtil.{getModuleData, getSbtModuleData}
import org.jetbrains.sbt.annotator.dependency.AddSbtDependencyUtils.{LIBRARY_DEPENDENCIES, SEQ, SETTINGS, getPossiblePlacesToAddFromProjectDefinition, getTopLevelLibraryDependencies, getTopLevelPlaceToAdd, getTopLevelSbtProjects, isAddableLibraryDependencies, isAddableSettings, toDependencyPlaceInfo}
import org.jetbrains.sbt.annotator.dependency.DependencyPlaceInfo
import org.jetbrains.sbt.project.data.{ModuleExtData, SbtModuleData}
import org.jetbrains.sbt.{RichFile, Sbt, SbtBundle, SbtUtil, language}

import java.io.File
import java.util
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class SbtDependencyModificator extends ExternalDependencyModificator{
  override def supports(module: OpenapiModule.Module): Boolean = SbtUtil.isSbtModule(module)

  override def addDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency): Unit = {

  }

  override def updateDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency, unifiedDependency1: UnifiedDependency): Unit = {

  }

  override def removeDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency): Unit = {

  }

  override def addRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }

  override def deleteRepository(module: OpenapiModule.Module, unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {

  }

  def handleReferenceExpression(refExpr: ScReferenceExpression): Seq[String] = {
    refExpr.resolve() match {
      case (_: ScReferencePattern) && inNameContext(ScPatternDefinition.expr(expr)) => expr match {
          case text: ScInfixExpr => ArraySeq(text.getText)
          case re: ScReferenceExpression => handleReferenceExpression(re)
          case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) => call.argumentExpressions.flatMap(argExpr => {
            argExpr match {
              case text: ScInfixExpr => ArraySeq(text.getText)
              case re: ScReferenceExpression => handleReferenceExpression(re)
            }
          }
          )
        }
      case _ => null
    }
  }

  def extractLibraryDependenciesFromInfixExprSeq(libDeps: Seq[ScInfixExpr]): Seq[Array[String]] = {
    if (libDeps.isEmpty) return Seq()
    val res = libDeps.flatMap(libDep => {
      libDep.right match {
        case moduleID: ScInfixExpr => ArraySeq(moduleID.getText)
        case re: ScReferenceExpression => handleReferenceExpression(re)
        case _ => ArraySeq()
      }
    }).filter(_ != null)
      .distinct
      .map(libDepText => {
      libDepText
        .split("%")
        .filter(_.nonEmpty) // Remove empty string
        .map(_.trim()) // Remove space
        .map(_.replaceAll("^\"|\"$", "")) // Remove double quotes
    })

    res
  }

  def extractLibraryDependenciesFromSettings(settings: ScMethodCall): Seq[Array[String]] = {
    if (!isAddableSettings(settings)) return Seq()
    var infixExprSeq: Seq[ScInfixExpr] = Seq()
    settings.args.exprs.foreach {
      case e: ScInfixExpr =>
        if (e.left.textMatches(LIBRARY_DEPENDENCIES) && isAddableLibraryDependencies(e)) infixExprSeq :+= e
      case _ =>
    }
    extractLibraryDependenciesFromInfixExprSeq(infixExprSeq)
  }

  def getLibraryDependenciesUtil(project: Project, module: OpenapiModule.Module, psiSbtFile: ScalaFile): Seq[Array[String]] = {
    var res: Seq[Array[String]] = Seq()

    val topLevelLibDeps: Seq[ScInfixExpr] = getTopLevelLibraryDependencies(psiSbtFile)

    val sbtProj = getSbtModuleData(module)

    val extractedTopLevelLibDeps = extractLibraryDependenciesFromInfixExprSeq(topLevelLibDeps)

    res ++= extractedTopLevelLibDeps

//    if (topLevelLibDeps.nonEmpty) res ++= topLevelLibDeps
//      .flatMap((elem: ScInfixExpr) => toDependencyPlaceInfo(elem, Seq())(project))

    val sbtProjects: Seq[ScPatternDefinition] = getTopLevelSbtProjects(psiSbtFile)

    val moduleName = module.getName

    val modules = List(module)
    def containsModuleName(proj: ScPatternDefinition, moduleName: String): Boolean =
      proj.getText.contains("\"" + moduleName + "\"")
    val projToAffectedModules = sbtProjects.map(proj => proj -> modules.map(_.getName).filter(containsModuleName(proj, _))).toMap

    val elemToAffectedProjects = collection.mutable.Map[PsiElement, Seq[String]]()
    sbtProjects.foreach(proj => {
      val places = getPossiblePlacesToAddFromProjectDefinition(proj)
      places.foreach(elem => {
        elemToAffectedProjects.update(elem, elemToAffectedProjects.getOrElse(elem, Seq()) ++ projToAffectedModules(proj))
      })
    })

    res ++= elemToAffectedProjects.toList
      .filter(_._2.contains(moduleName))
      .map(_._1)
      .flatMap(elem => {
        elem match {
          case e: ScInfixExpr => extractLibraryDependenciesFromInfixExprSeq(Seq(e))
          case settings: ScMethodCall => extractLibraryDependenciesFromSettings(settings)
        }
      })
//      .flatMap(elem => extractLibraryDependenciesFromSettings(elem))

//    res ++= elemToAffectedProjects.toList
//      .sortWith(_._2.toString < _._2.toString)
//      .sortWith(_._2.contains(moduleName) && !_._2.contains(moduleName))
//      .map(_._1)
//      .flatMap(elem => toDependencyPlaceInfo(elem, elemToAffectedProjects(elem))(project))
//
//    res ++= getTopLevelPlaceToAdd(psiSbtFile)(project).toList
//
//    res.distinct
    res
  }

  def getLibraryDependencies(sbtFileOpt: Some[VirtualFile], project: Project, module: OpenapiModule.Module): Seq[Array[String]] = {
    val libDeps = inReadAction(
      for {
        sbtFile <- sbtFileOpt
        psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
        depPlaces = getLibraryDependenciesUtil(project, module, psiSbtFile)
      } yield depPlaces
    )
    libDeps.getOrElse(Seq.empty)
  }

  override def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = {

    val project = module.getProject
    var scalaVer: String = ""
    val moduleExtData = SbtUtil.getModuleData(
      project,
      ExternalSystemApiUtil.getExternalProjectId(module),
      ModuleExtData.Key).toSeq
    if (moduleExtData.nonEmpty) scalaVer = moduleExtData.head.scalaVersion

    val sbtProj: File = getSbtModuleData(module) match {
      case Some(sbtModuleData: SbtModuleData) => new File(sbtModuleData.buildURI.toString.stripPrefix("file:"))
      case _ => null
    }
    if (sbtProj == null) return List().asJava
    val sbtFilePath = sbtProj / Sbt.BuildFile
    if (!sbtFilePath.exists()) return List().asJava
    val virtualSbtFilePath = LocalFileSystem.getInstance().findFileByPath(sbtFilePath.getAbsolutePath)
    val sbtFileOpt = Some(virtualSbtFilePath)

    val libDeps = getLibraryDependencies(sbtFileOpt, project, module)
//    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module)
//    val dataNodes = SbtUtil.getModuleData(project, moduleId, ProjectKeys.LIBRARY_DEPENDENCY)
//
//    dataNodes.map(libDependencyData => {
//
//      val dataContext = new DataContext {
//        override def getData(dataId: String): AnyRef = null
//      }
//
//      val libs = libDependencyData.getExternalName.split(":")
//      libs.length match {
//        case x if x <= 3 => new DeclaredDependency(new UnifiedDependency(null, null, null, null), dataContext)
//        case _ => new DeclaredDependency(new UnifiedDependency(libs(0), libs(1), libs(2), libDependencyData.getScope.toString), dataContext)
//      }
//    }).toList.asJava

    val dataContext = new DataContext {
      override def getData(dataId: String): AnyRef = null
    }
    libDeps.map(libDep => {

      libDep.length match {
        case x if x < 3 || x > 4 => null
        case x if x == 3 => new DeclaredDependency(
          new UnifiedDependency(
            libDep(0),
            SbtCommon.buildScalaDependencyString(libDep(1), scalaVer),
            libDep(2),
            SbtCommon.defaultLibConfiguration),
          dataContext)
        case x if x == 4 => new DeclaredDependency(
          new UnifiedDependency(
            libDep(0),
            SbtCommon.buildScalaDependencyString(libDep(1), scalaVer),
            libDep(2),
            libDep(3)),
          dataContext)
      }
    }).filter(_ != null).toList.asJava
  }

  override def declaredRepositories(module: OpenapiModule.Module): util.List[UnifiedDependencyRepository] = {
    List().asJava
  }
}
