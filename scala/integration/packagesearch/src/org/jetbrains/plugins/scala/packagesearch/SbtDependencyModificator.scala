package org.jetbrains.plugins.scala.packagesearch

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.openapi.{module => OpenapiModule}
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.{ProjectContext}
import org.jetbrains.sbt.SbtUtil.{getSbtModuleData}
import org.jetbrains.sbt.annotator.dependency.AddSbtDependencyUtils.{LIBRARY_DEPENDENCIES, SEQ, SETTINGS, getPossiblePlacesToAddFromProjectDefinition, getTopLevelLibraryDependencies, getTopLevelSbtProjects, isAddableLibraryDependencies, isAddableSettings}
import org.jetbrains.sbt.project.data.{ModuleExtData, SbtModuleData}
import org.jetbrains.sbt.{RichFile, Sbt, SbtUtil}

import java.io.File
import java.util
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class SbtDependencyModificator extends ExternalDependencyModificator{
  override def supports(module: OpenapiModule.Module): Boolean = SbtUtil.isSbtModule(module)

  override def addDependency(module: OpenapiModule.Module, unifiedDependency: UnifiedDependency): Unit = {

  }

  override def updateDependency(module: OpenapiModule.Module, currentDependency: UnifiedDependency, newDependency: UnifiedDependency): Unit = {
    val sbtFileOpt = getSbtFileOpt(module)
    val currentCoordinates = currentDependency.getCoordinates
    val currentDepText: String = generateArtifactText(currentCoordinates.getGroupId, currentCoordinates.getArtifactId, currentCoordinates.getVersion, currentDependency.getScope)
    val newCoordinates = newDependency.getCoordinates
    implicit val project: Project = module.getProject
    val libDeps = getLibraryDependencies(sbtFileOpt, project, module, identityFunc)(project)
    libDeps.foreach(
      libDep => {
        val processedDep = postProcessDependency(libDep.asInstanceOf[ScInfixExpr].getText)
        var processedDepText: String = ""
        processedDep match {
          case Array(a,b,c) => processedDepText = generateArtifactText(a, b, c, SbtCommon.defaultLibScope)
          case Array(a,b,c,d) => processedDepText = generateArtifactText(a, b, c, d)
          case _ =>
        }

        if (currentDepText.equals(processedDepText)) {
          val newDepText: String = generateArtifactText(newCoordinates.getGroupId, newCoordinates.getArtifactId, newCoordinates.getVersion, newDependency.getScope)
          inWriteCommandAction(libDep.asInstanceOf[ScInfixExpr].replace(code"$newDepText"))

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

  def generateArtifactText(groupId: String, artifactId: String, version: String, configuration: String): String = {
    var artifactText = ""
    if (artifactId.matches("^.+_\\d+\\.\\d+$"))
      artifactText += s""""${groupId}" %% "${artifactId.replaceAll("_\\d+\\.\\d+$", "")}" % "${version}""""
    else
      artifactText += s""""${groupId}" %% "${artifactId}" % "${version}""""

    if (configuration != SbtCommon.defaultLibScope) {
      artifactText += s""" % $configuration"""
    }
    artifactText
  }

  def handleReferenceExpression(refExpr: ScReferenceExpression, retriever: (ScInfixExpr) => Any)(implicit projectContext: ProjectContext): Seq[Any] = try {
    refExpr.resolve() match {
      case (_: ScReferencePattern) && inNameContext(ScPatternDefinition.expr(expr)) => expr match {
          case text: ScInfixExpr => ArraySeq(retriever(text))
          case re: ScReferenceExpression => handleReferenceExpression(re, retriever)
          case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) => call.argumentExpressions.flatMap(argExpr => {
            argExpr match {
              case text: ScInfixExpr => ArraySeq(retriever(text))
              case re: ScReferenceExpression => handleReferenceExpression(re, retriever)
            }
          }
          )
        }
      case _ => null
    }
  } catch {
    case e : Exception =>
      throw e
  }

  def extractLibraryDependenciesFromInfixExprSeq(libDeps: Seq[ScInfixExpr], retriever: (ScInfixExpr) => Any)(implicit projectContext: ProjectContext): Seq[Any] = {
    if (libDeps.isEmpty) return Seq()
    val res = libDeps.flatMap(libDep => {
      libDep.right match {
        case moduleID: ScInfixExpr => ArraySeq(retriever(moduleID))
        case re: ScReferenceExpression => handleReferenceExpression(re, retriever)
        case other => ArraySeq()
      }
    }).filter(_ != null)
      .distinct

    res
  }

  def extractLibraryDependenciesFromSettings(settings: ScMethodCall, retriever: (ScInfixExpr) => Any)(implicit projectContext: ProjectContext): Seq[Any] = {
    if (!isAddableSettings(settings)) return Seq()
    var infixExprSeq: Seq[ScInfixExpr] = Seq()
    settings.args.exprs.foreach {
      case e: ScInfixExpr =>
        if (e.left.textMatches(LIBRARY_DEPENDENCIES) && isAddableLibraryDependencies(e)) infixExprSeq :+= e
      case _ =>
    }
    extractLibraryDependenciesFromInfixExprSeq(infixExprSeq, retriever)
  }

  def getLibraryDependenciesUtil(project: Project, module: OpenapiModule.Module, psiSbtFile: ScalaFile, retriever: (ScInfixExpr) => Any)(implicit projectContext: ProjectContext): Seq[Any] = {
    var res: Seq[Any] = Seq()

    val topLevelLibDeps: Seq[ScInfixExpr] = getTopLevelLibraryDependencies(psiSbtFile)

//    val sbtProj = getSbtModuleData(module)

    val extractedTopLevelLibDeps = extractLibraryDependenciesFromInfixExprSeq(topLevelLibDeps, retriever)

    res ++= extractedTopLevelLibDeps

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
          case e: ScInfixExpr => extractLibraryDependenciesFromInfixExprSeq(Seq(e), retriever)
          case settings: ScMethodCall => extractLibraryDependenciesFromSettings(settings, retriever)
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

  def getLibraryDependencies(sbtFileOpt: Some[VirtualFile], project: Project, module: OpenapiModule.Module, retriever: (ScInfixExpr) => Any)(implicit projectContext: ProjectContext): Seq[Any] = {
    val libDeps = inReadAction(
      for {
        sbtFile <- sbtFileOpt
        psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
        depPlaces = getLibraryDependenciesUtil(project, module, psiSbtFile, retriever)
      } yield depPlaces
    )
    libDeps.getOrElse(Seq.empty)
  }

  def getTextFromInfix(elem: ScInfixExpr): Any = elem.getText
  def postProcessDependency(dependency: String): Array[String] = {
    dependency.split("%")
      .filter(_.nonEmpty) // Remove empty string
      .map(_.trim()) // Remove space
      .map(_.replaceAll("^\"|\"$", "")) // Remove double quotes
  }
  def identityFunc(elem: ScInfixExpr): Any = elem

  def getSbtFileOpt(module: OpenapiModule.Module): Some[VirtualFile] = {
    val sbtProj: File = getSbtModuleData(module) match {
      case Some(sbtModuleData: SbtModuleData) => new File(sbtModuleData.buildURI.toString.stripPrefix("file:"))
      case _ => null
    }
    if (sbtProj == null) return null
    val sbtFilePath = sbtProj / Sbt.BuildFile
    if (!sbtFilePath.exists()) return null
    val virtualSbtFilePath = LocalFileSystem.getInstance().findFileByPath(sbtFilePath.getAbsolutePath)
    Some(virtualSbtFilePath)
  }

  override def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = {

    val project = module.getProject
    var scalaVer: String = ""
    val moduleExtData = SbtUtil.getModuleData(
      project,
      ExternalSystemApiUtil.getExternalProjectId(module),
      ModuleExtData.Key).toSeq
    if (moduleExtData.nonEmpty) scalaVer = moduleExtData.head.scalaVersion

    val sbtFileOpt = getSbtFileOpt(module)
    if (sbtFileOpt == null) return List().asJava

    val libDepsInfix = getLibraryDependencies(sbtFileOpt, project, module, identityFunc)(project)

    val dataContext = new DataContext {
      override def getData(dataId: String): AnyRef = null
    }

    inReadAction({
      libDepsInfix.map(libDepInfix => {
        val libDepArr = postProcessDependency(libDepInfix.asInstanceOf[ScInfixExpr].getText)
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
//    postProcessDependencySeq(libDeps).map(libDep => {
//
//      libDep.length match {
//        case x if x < 3 || x > 4 => null
//        case x if x == 3 => new DeclaredDependency(
//          new UnifiedDependency(
//            libDep(0),
//            SbtCommon.buildScalaDependencyString(libDep(1), scalaVer),
//            libDep(2),
//            SbtCommon.defaultLibConfiguration),
//          dataContext)
//        case x if x == 4 => new DeclaredDependency(
//          new UnifiedDependency(
//            libDep(0),
//            SbtCommon.buildScalaDependencyString(libDep(1), scalaVer),
//            libDep(2),
//            libDep(3)),
//          dataContext)
//      }
//    }).filter(_ != null).toList.asJava
  }

  override def declaredRepositories(module: OpenapiModule.Module): util.List[UnifiedDependencyRepository] = {
    List().asJava
  }
}
