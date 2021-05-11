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
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.openapi.{module => OpenapiModule}
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall, ScReferenceExpression, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.packagesearch.SbtDependencyUtils.{LIBRARY_DEPENDENCIES, SEQ, SETTINGS, getPossiblePlacesToAddFromProjectDefinition, getTopLevelLibraryDependencies, getTopLevelPlaceToAdd, getTopLevelSbtProjects, isAddableLibraryDependencies, isAddableSettings, toDependencyPlaceInfo}
import org.jetbrains.plugins.scala.packagesearch.ui.AddDependencyPreviewWizard
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}
import org.jetbrains.sbt.SbtUtil.getSbtModuleData
import org.jetbrains.sbt.annotator.dependency.DependencyPlaceInfo
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.{ModuleExtData, SbtModuleData}
import org.jetbrains.sbt.{RichFile, Sbt, SbtUtil, language}

import java.io.File
import java.util
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class SbtDependencyModificator extends ExternalDependencyModificator{
  val GET_PLACES_MODE: String = "places"
  val GET_LIBRARIES_MODE: String = "libraries"

  override def supports(module: OpenapiModule.Module): Boolean = SbtUtil.isSbtModule(module)

  def refresh(project: Project): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }

  override def addDependency(module: OpenapiModule.Module, newDependency: UnifiedDependency): Unit = {
    val project = module.getProject
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return
    val dependencyPlaces = getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, identityFunc, GET_PLACES_MODE)(project)
    val newDependencyCoordinates = newDependency.getCoordinates
    val newArtifactInfo = ArtifactInfo(newDependencyCoordinates.getGroupId, newDependencyCoordinates.getArtifactId, newDependencyCoordinates.getVersion, newDependency.getScope)

    ApplicationManager.getApplication.invokeLater { () =>
      val wizard = new AddDependencyPreviewWizard(
        project,
        Some(newArtifactInfo),
        dependencyPlaces.map {
          case Some(inside: DependencyPlaceInfo) => inside
          case outside: DependencyPlaceInfo => outside
          case _ => null
        }.filter(_ != null))
      wizard.search() match {
        case Some(fileLine) =>
          SbtDependencyUtils.addDependency(fileLine.element, newArtifactInfo)(project)
          refresh(project)
        case _ =>
      }
    }
  }

  override def updateDependency(module: OpenapiModule.Module, currentDependency: UnifiedDependency, newDependency: UnifiedDependency): Unit = {
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    val currentCoordinates = currentDependency.getCoordinates
    val currentDepText: String = SbtDependencyUtils.generateArtifactTextVerbose(currentCoordinates.getGroupId, currentCoordinates.getArtifactId, currentCoordinates.getVersion, currentDependency.getScope)
    val newCoordinates = newDependency.getCoordinates
    implicit val project: Project = module.getProject
    val libDeps = getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, identityFunc)(project)
    libDeps.foreach(
      libDep => {
        var processedDep: Array[String] = Array()
        processedDep = postProcessDependency(getTextFromInfix(libDep.asInstanceOf[ScInfixExpr]).asInstanceOf[String])
        var processedDepText: String = ""
        processedDep match {
          case Array(a,b,c) => processedDepText = SbtDependencyUtils.generateArtifactTextVerbose(a, b, c, SbtCommon.defaultLibScope)
          case Array(a,b,c,d) => processedDepText = SbtDependencyUtils.generateArtifactTextVerbose(a, b, c, d)
          case _ =>
        }

        if (currentDepText.equals(processedDepText)) {
          val newDepText: String = SbtDependencyUtils.generateArtifactTextVerbose(newCoordinates.getGroupId, newCoordinates.getArtifactId, newCoordinates.getVersion, newDependency.getScope)
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

  def handleReferenceExpression(refExpr: ScReferenceExpression,
                                retriever: (ScInfixExpr) => Any,
                                mode: String)
                               (implicit projectContext: ProjectContext): Seq[Any] = try {
    refExpr.resolve() match {
      case (_: ScReferencePattern) && inNameContext(ScPatternDefinition.expr(expr)) => expr match {
          case text: ScInfixExpr =>
            if (mode == GET_PLACES_MODE) ArraySeq()
            else ArraySeq(retriever(text))
          case re: ScReferenceExpression =>
            handleReferenceExpression(re, retriever, mode)
          case seq: ScMethodCall if seq.deepestInvokedExpr.textMatches(SEQ) =>
            extractLibraryDependenciesOrPlacesFromSeq(seq, retriever, mode)
        }
      case _ => null
    }
  } catch {
    case e : Exception =>
      throw e
  }

  def handleInfixExpr(infixExpr: ScInfixExpr,
                      retriever: (ScInfixExpr) => Any,
                      mode: String)
                     (implicit projectContext: ProjectContext): Seq[Any] = try {
    if (mode == GET_PLACES_MODE)
      ArraySeq(toDependencyPlaceInfo(infixExpr.asInstanceOf[PsiElement], Seq())(projectContext))
    else {
      if (infixExpr.operation.refName == "++") {
        var leftRes: Seq[Any] = Seq.empty
        var rightRes: Seq[Any] = Seq.empty
        infixExpr.left match {
          case call: ScMethodCall => leftRes = extractLibraryDependenciesOrPlacesFromMethodCall(call, retriever, mode)(projectContext)
          case subInfix: ScInfixExpr => leftRes = handleInfixExpr(subInfix, retriever, mode)(projectContext)
          case refExpression: ScReferenceExpression => leftRes = handleReferenceExpression(refExpression, retriever, mode)(projectContext)
          case _ =>
        }

        infixExpr.right match {
          case call: ScMethodCall => rightRes = extractLibraryDependenciesOrPlacesFromMethodCall(call, retriever, mode)(projectContext)
          case subInfix: ScInfixExpr => rightRes = handleInfixExpr(subInfix, retriever, mode)(projectContext)
          case refExpression: ScReferenceExpression => rightRes = handleReferenceExpression(refExpression, retriever, mode)(projectContext)
          case _ =>
        }
        leftRes ++ rightRes
      }
      else
        ArraySeq(retriever(infixExpr))
    }
  } catch {
    case e: Exception =>
      throw e
  }

  def extractLibraryDependenciesOrPlacesFromInfixExprSeq(libDeps: Seq[ScInfixExpr],
                                                         retriever: (ScInfixExpr) => Any,
                                                         mode: String)
                                                        (implicit projectContext: ProjectContext): Seq[Any] = {
    if (libDeps.isEmpty) return Seq()
    val res = libDeps.flatMap(libDep => {
      libDep.right match {
        case moduleID: ScInfixExpr => handleInfixExpr(moduleID, retriever, mode)(projectContext)
        case re: ScReferenceExpression =>
          handleReferenceExpression(re, retriever, mode)
        case _ => ArraySeq()
      }
    }).filter(_ != null)
      .distinct

    res
  }

  def extractLibraryDependenciesOrPlacesFromSeq(seq: ScMethodCall,
                                                retriever: (ScInfixExpr) => Any,
                                                mode: String)
                                               (implicit projectContext: ProjectContext): Seq[Any] = {
    if (mode == GET_PLACES_MODE)
      Seq(toDependencyPlaceInfo(seq.asInstanceOf[PsiElement], Seq())(projectContext))
    else {
      seq.argumentExpressions.flatMap(argExpr => {
        argExpr match {
          case text: ScInfixExpr => ArraySeq(retriever(text))
          case re: ScReferenceExpression => handleReferenceExpression(re, retriever, mode)
          case _: ScMethodCall => Seq()
          case _ => Seq()
        }
      }
      )
    }
  }

  def extractLibraryDependenciesOrPlacesFromSettings(settings: ScMethodCall,
                                                     retriever: (ScInfixExpr) => Any,
                                                     mode: String)
                                                    (implicit projectContext: ProjectContext): Seq[Any] = {
    if (mode == GET_PLACES_MODE)
      Seq(toDependencyPlaceInfo(settings.asInstanceOf[PsiElement], Seq())(projectContext))
    else {
      var infixExprSeq: Seq[ScInfixExpr] = Seq()
      settings.args.exprs.foreach {
        case e: ScInfixExpr =>
          if (e.left.textMatches(LIBRARY_DEPENDENCIES) && isAddableLibraryDependencies(e)) infixExprSeq :+= e
        case _ =>
      }
      extractLibraryDependenciesOrPlacesFromInfixExprSeq(infixExprSeq, retriever, mode)
    }
  }


  def extractLibraryDependenciesOrPlacesFromMethodCall(methodCall: ScMethodCall,
                                                       retriever: (ScInfixExpr) => Any,
                                                       mode: String)
                                                      (implicit projectContext: ProjectContext): Seq[Any] = {
    methodCall match {
      case settings if isAddableSettings(settings) =>
        settings.getEffectiveInvokedExpr match {
          case expr: ScReferenceExpression if expr.refName == SETTINGS =>
            extractLibraryDependenciesOrPlacesFromSettings(settings, retriever, mode)
          case _ => Seq()
        }
      case seq if seq.deepestInvokedExpr.textMatches(SEQ) =>
        extractLibraryDependenciesOrPlacesFromSeq(seq, retriever, mode)
      case _ => Seq()
    }

  }



  def getLibraryDependenciesOrPlacesUtil(project: Project,
                                         module: OpenapiModule.Module,
                                         psiSbtFile: ScalaFile,
                                         retriever: (ScInfixExpr) => Any,
                                         mode: String)
                                        (implicit projectContext: ProjectContext): Seq[Any] = {
    var res: Seq[Any] = Seq()

    val topLevelLibDeps: Seq[ScInfixExpr] = getTopLevelLibraryDependencies(psiSbtFile)

    val sbtProj = getSbtModuleData(module)

    var extractedTopLevelLibDeps: Seq[Any] = Seq()

    extractedTopLevelLibDeps = extractLibraryDependenciesOrPlacesFromInfixExprSeq(topLevelLibDeps, retriever, mode)

    res ++= extractedTopLevelLibDeps

    val sbtProjects: Seq[ScPatternDefinition] = getTopLevelSbtProjects(psiSbtFile)

    val moduleName = module.getName

    val modules = List(module)
    def containsModuleName(proj: ScPatternDefinition, moduleName: String): Boolean =
      proj.getText.contains("\"" + moduleName + "\"")
    val projToAffectedModules = sbtProjects.map(proj => proj -> modules.filter(module => {
      SbtUtil.getSbtModuleData(module) match {
        case Some(moduleData: SbtModuleData) =>
          proj.getText.contains(moduleData.id) || containsModuleName(proj, module.getName)
        case _ => containsModuleName(proj, module.getName)
      }

    }).map(_.getName)).toMap

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
          case e: ScInfixExpr => extractLibraryDependenciesOrPlacesFromInfixExprSeq(Seq(e), retriever, mode)
          case settings: ScMethodCall =>
            extractLibraryDependenciesOrPlacesFromMethodCall(settings, retriever, mode)
        }
      })

      if (mode == GET_PLACES_MODE) res ++= getTopLevelPlaceToAdd(psiSbtFile)(project).toList


    res.distinct
  }

  def getLibraryDependenciesOrPlaces(sbtFileOpt: Option[VirtualFile],
                                     project: Project,
                                     module: OpenapiModule.Module,
                                     retriever: (ScInfixExpr) => Any = identityFunc,
                                     mode: String = GET_LIBRARIES_MODE)
                                    (implicit projectContext: ProjectContext): Seq[Any] = try {
    val libDeps = inReadAction(
      for {
        sbtFile <- sbtFileOpt
        psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
        deps = getLibraryDependenciesOrPlacesUtil(project, module, psiSbtFile, retriever, mode)
      } yield deps
    )
    libDeps.getOrElse(Seq.empty)
  } catch {
    case e: Exception =>
      throw(e)
    case _: Throwable => Seq()
  }

  def getTextFromInfix(elem: ScInfixExpr): Any = {
    var result = ""
    inReadAction({result = elem.getText})
    result
  }
  def postProcessDependency(dependency: String): Array[String] = {
    dependency.split("%")
      .filter(_.nonEmpty) // Remove empty string
      .map(_.trim()) // Remove space
      .map(_.replaceAll("^\"|\"$", "")) // Remove double quotes
  }
  def identityFunc(elem: ScInfixExpr): Any = elem



  override def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = {
    print(module.getName)
    val project = module.getProject
    var scalaVer: String = ""
    val moduleExtData = SbtUtil.getModuleData(
      project,
      ExternalSystemApiUtil.getExternalProjectId(module),
      ModuleExtData.Key).toSeq
    if (moduleExtData.nonEmpty) scalaVer = moduleExtData.head.scalaVersion

    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return List().asJava

    val libDepsInfix = getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, identityFunc)(project)

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
  }

  override def declaredRepositories(module: OpenapiModule.Module): util.List[UnifiedDependencyRepository] = {
    List().asJava
  }
}
