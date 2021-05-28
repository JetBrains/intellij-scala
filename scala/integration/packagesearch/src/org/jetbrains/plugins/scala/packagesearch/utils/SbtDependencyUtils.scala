package org.jetbrains.plugins.scala.packagesearch.utils

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.{Module => OpenapiModule}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.intellij.psi.{PsiElement, PsiFile, PsiManager, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall, ScReferenceExpression, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode.ScalaCodeContext
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.packagesearch.ArtifactInfo
import org.jetbrains.plugins.scala.packagesearch.utils.SbtDependencyUtils.GetMode.{GetDep, GetPlace}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}
import org.jetbrains.sbt.SbtUtil.getSbtModuleData
import org.jetbrains.sbt.annotator.dependency.DependencyPlaceInfo
import org.jetbrains.sbt.{RichFile, Sbt, SbtUtil, language}
import org.jetbrains.sbt.project.data.SbtModuleData

import java.io.File

object SbtDependencyUtils {
  val LIBRARY_DEPENDENCIES: String = "libraryDependencies"
  val SETTINGS: String = "settings"
  val SEQ: String = "Seq"
  val ANY: String = "Any"

  val SBT_PROJECT_TYPE = "_root_.sbt.Project"
  val SBT_SEQ_TYPE = "_root_.scala.collection.Seq"
  val SBT_SETTING_TYPE = "_root_.sbt.Def.Setting"

  private val InfixOpsSet = Set(":=", "+=", "++=")

  sealed trait GetMode
  object GetMode {
    case object GetPlace extends GetMode
    case object GetDep extends GetMode
  }

  def buildScalaDependencyString(artifactID: String, scalaVer: String): String = {
    val ver = scalaVer.split('.')
    s"${artifactID}_${ver(0)}.${ver(1)}"
  }

  def findLibraryDependency(project: Project,
                            module: OpenapiModule,
                            dependency: UnifiedDependency,
                            versionRequired: Boolean = true,
                            configurationRequired: Boolean = true):ScInfixExpr = {
    val sbtFileOpt = getSbtFileOpt(module)
    val targetCoordinates = dependency.getCoordinates
    val targetDepText: String = generateArtifactTextVerbose(
      targetCoordinates.getGroupId,
      targetCoordinates.getArtifactId,
      if (versionRequired) targetCoordinates.getVersion else "",
      if (configurationRequired) dependency.getScope else SbtCommon.defaultLibScope)
    val libDeps = getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, GetDep)
    libDeps.foreach(
      libDep => {
        var processedDep: Array[String] = Array()
        processedDep = postProcessDependency(getTextFromInfixAndString(libDep.asInstanceOf[(ScInfixExpr, String)]))
        var processedDepText: String = ""
        processedDep match {
          case Array(a,b,c) =>
            processedDepText = generateArtifactTextVerbose(
              a,
              b,
              if (versionRequired) c else "",
              SbtCommon.defaultLibScope)
          case Array(a,b,c,d) =>
            processedDepText = generateArtifactTextVerbose(
              a,
              b,
              if (versionRequired) c else "",
              if (configurationRequired) d else SbtCommon.defaultLibScope)
          case _ =>
        }

        if (targetDepText.equals(processedDepText)) {
          return libDep.asInstanceOf[(ScInfixExpr, String)]._1
        }
      }
    )
    null
  }


  def getLibraryDependenciesOrPlacesUtil(module: OpenapiModule,
                                         psiSbtFile: ScalaFile,
                                         mode: GetMode): Seq[(PsiElement, String)] = try {
    var res: Seq[(PsiElement, String)] = Seq()

    val topLevelLibDeps: Seq[ScInfixExpr] = getTopLevelLibraryDependencies(psiSbtFile)

    val sbtProj = getSbtModuleData(module)

    var extractedTopLevelLibDeps: Seq[(PsiElement, String)] = Seq()

    extractedTopLevelLibDeps = topLevelLibDeps.flatMap(libDep => getLibraryDependenciesFromPsi(libDep, mode))

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
        case _ =>
          containsModuleName(proj, module.getName)
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
      .flatMap(elem => getLibraryDependenciesFromPsi(elem, mode))

//    if (mode == GetPlace) res ++= getTopLevelPlaceToAdd(psiSbtFile)(project).toList


    res.distinct
  } catch {
    case e: Exception => throw(e)
  }

  def getLibraryDependenciesOrPlaces(sbtFileOpt: Option[VirtualFile],
                                     project: Project,
                                     module: OpenapiModule,
                                     mode: GetMode): Seq[(PsiElement, String)] = try {
    val libDeps = inReadAction(
      for {
        sbtFile <- sbtFileOpt
        psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
        deps = getLibraryDependenciesOrPlacesUtil(module, psiSbtFile, mode)
      } yield deps
    )
    libDeps.getOrElse(Seq.empty)
  } catch {
    case e: Exception =>
      throw(e)
  }

  def getTextFromInfixAndString(elem: (ScInfixExpr, String)): String = {
    elem._2 match {
      case "" => elem._1.getText
      case _ => s"${elem._1.getText}%${elem._2}"
    }
  }

  def postProcessDependency(dependency: String): Array[String] = {
    dependency.split("%")
      .filter(_.nonEmpty) // Remove empty string
      .map(_.trim()) // Remove space
      .map(_.replaceAll("^\"|\"$", "")) // Remove double quotes
  }

  def getLibraryDependenciesFromPsi(psi: PsiElement, mode: GetMode): Seq[(PsiElement, String)] = {
    var result: Seq[(PsiElement, String)] = List()

    def callbackDep(psiElement: PsiElement):Unit = {
      psiElement match {
        case infix: ScInfixExpr if infix.operation.refName.contains("%") =>
          infix.getText.count(_ == '%') match {
            case 1 => inReadAction {
              val configuration = infix.right.getText
              def callbackRef(psiElement: PsiElement):Unit = {
                psiElement match {
                  case subInfix: ScInfixExpr if subInfix.operation.refName.contains("%") =>
                    result ++= Seq((subInfix, configuration))
                  case _ =>
                }
              }
              infix.left match {
                case refExpr: ScReferenceExpression =>
                  SbtDependencyTraverser.traverseReferenceExpr(refExpr)(callbackRef)
                case _ =>
              }
            }
            case _ => result ++= Seq((infix, ""))
          }

        case _ =>
      }
    }

    def callbackPlace(psiElement: PsiElement): Unit = {
      psiElement match {
        case libDep: ScInfixExpr if libDep.left.textMatches(LIBRARY_DEPENDENCIES) & isAddableLibraryDependencies(libDep) =>
          result ++= Seq((libDep, ""))
        case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) =>
          result ++= Seq((call, ""))
        case settings: ScMethodCall =>
          settings.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == SETTINGS => result ++= Seq((settings, ""))
            case _ =>
          }
        case _ =>
      }
    }

    def callback(psiElement: PsiElement):Unit = {
      if (mode == GetDep) callbackDep(psiElement)
      else callbackPlace(psiElement)
    }

    psi match {
      case infix: ScInfixExpr => SbtDependencyTraverser.traverseInfixExpr(infix)(callback)
      case call: ScMethodCall => SbtDependencyTraverser.traverseMethodCall(call)(callback)
      case refExpr: ScReferenceExpression => SbtDependencyTraverser.traverseReferenceExpr(refExpr)(callback)
      case _ =>
    }

    result
  }

  def getPossiblePlacesToAddFromProjectDefinition(proj: ScPatternDefinition): Seq[PsiElement] = {
    var res: Seq[PsiElement] = List()

    def action(psiElement: PsiElement): Unit = {
      psiElement match {
        case e: ScInfixExpr if e.left.textMatches(LIBRARY_DEPENDENCIES) && isAddableLibraryDependencies(e) => res ++= Seq(e)
        case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) => res ++= Seq(call)
        case typedSeq: ScTypedExpression if typedSeq.isSequenceArg =>
          typedSeq.expr match {
            case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) => res ++= Seq(typedSeq)
            case _ =>
          }
        case settings: ScMethodCall =>
          settings.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == SETTINGS => res ++= Seq(settings)
            case _ =>
          }
        case _ =>
      }
    }

    SbtDependencyTraverser.traversePatternDef(proj)(action)

    res
  }

  def getTopLevelSbtProjects(psiSbtFile: ScalaFile): Seq[ScPatternDefinition] = try {
    var res: Seq[ScPatternDefinition] = List()

    psiSbtFile.acceptChildren(new ScalaElementVisitor {
      override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
        if (pat.expr.isEmpty)
          return

        if (pat.expr.get.`type`().getOrAny.canonicalText != SBT_PROJECT_TYPE)
          return

        res = res ++ Seq(pat)
        super.visitPatternDefinition(pat)
      }
    })

    res
  } catch {
    case e: Exception =>
      throw(e)
  }

  def getTopLevelLibraryDependencies(psiSbtFile: ScalaFile): Seq[ScInfixExpr] = {
    var res: Seq[ScInfixExpr] = List()

    psiSbtFile.acceptChildren(new ScalaElementVisitor {
      override def visitInfixExpression(infix: ScInfixExpr): Unit = {
        if (infix.left.textMatches(LIBRARY_DEPENDENCIES) && infix.getParent.isInstanceOf[PsiFile]) {
          res = res ++ Seq(infix)
        }
      }
    })

    res
  }

  def getTopLevelPlaceToAdd(psiFile: ScalaFile)(implicit project: Project): Option[DependencyPlaceInfo] = {
    val line: Int = StringUtil.offsetToLineNumber(psiFile.charSequence, psiFile.getTextLength) + 1
    getRelativePath(psiFile).map { relpath =>
      DependencyPlaceInfo(relpath, psiFile.getTextLength, line, psiFile, Seq())
    }
  }

  def addDependency(expr: PsiElement, info: ArtifactInfo)(implicit project: Project): Option[PsiElement] = {
    expr match {
      case e: ScInfixExpr if e.left.textMatches(LIBRARY_DEPENDENCIES) => addDependencyToLibraryDependencies(e, info)
      case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) => addDependencyToSeq(call, info)
      case typedSeq: ScTypedExpression if typedSeq.isSequenceArg => addDependencyToTypedSeq(typedSeq, info)
      case settings: ScMethodCall =>
        settings.getEffectiveInvokedExpr match {
          case expr: ScReferenceExpression if expr.refName == SETTINGS =>
            Option(addDependencyToSettings(settings, info)(project))
          case _ => None
        }
      case file: PsiFile =>
        Option(addDependencyToFile(file, info)(project))
      case _ => None
    }
  }

  def addDependencyToLibraryDependencies(infix: ScInfixExpr, info: ArtifactInfo)(implicit project: Project): Option[PsiElement] = {

    val psiFile = infix.getContainingFile

    infix.operation.refName match {
      case "+=" =>
        val dependency: ScExpression = infix.right
        val seqCall: ScMethodCall = generateSeqPsiMethodCall

        doInSbtWriteCommandAction({
          seqCall.args.addExpr(dependency.copy().asInstanceOf[ScExpression])
          seqCall.args.addExpr(generateArtifactPsiExpression(info)(project))
          infix.operation.replace(ScalaPsiElementFactory.createElementFromText("++=")(project))
          dependency.replace(seqCall)
        }, psiFile)

        Option(infix.right)

      case "++=" =>
        val dependencies: ScExpression = infix.right
        dependencies match {
          case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) =>
            val addedExpr = generateArtifactPsiExpression(info)(project)
            doInSbtWriteCommandAction(call.args.addExpr(addedExpr), psiFile)
            Option(addedExpr)
          case subInfix: ScInfixExpr if subInfix.operation.refName == "++" =>
//            val seq: ScMethodCall = generateSeqPsiMethodCall
            doInSbtWriteCommandAction({
//              seq.args.addExpr(generateArtifactPsiExpression(info)(project))
//              infix.right.addAfter(ScalaPsiElementFactory.createElementFromText(" ")(project), infix.getLastChild)
//              infix.right.addAfter(ScalaPsiElementFactory.createElementFromText("++")(project), infix.getLastChild)
//              infix.right.addAfter(ScalaPsiElementFactory.createElementFromText(" ")(project), infix.getLastChild)
//              infix.right.addAfter(seq, infix.getLastChild)
//              infix.replace(code"${infix} ++ $seq")
              subInfix.replace(ScalaPsiElementFactory.createExpressionFromText(s"${subInfix.getText} ++ Seq(${generateArtifactText(info)})")(project))
            }, psiFile)
            Option(infix.right)
          case _ => None
        }

      case _ => None
    }
  }

  def addDependencyToSeq(seqCall: ScMethodCall, info: ArtifactInfo)(implicit project: Project): Option[PsiElement] = {
    val addedExpr = if (!seqCall.`type`().getOrAny.canonicalText.contains(SBT_SETTING_TYPE))
      generateArtifactPsiExpression(info) else generateLibraryDependency(info)
    doInSbtWriteCommandAction(seqCall.args.addExpr(addedExpr), seqCall.getContainingFile)
    Some(addedExpr)
  }

  def addDependencyToTypedSeq(typedSeq: ScTypedExpression, info: ArtifactInfo)(implicit project: Project): Option[PsiElement] =
    typedSeq.expr match {
      case seqCall: ScMethodCall =>
        val addedExpr = generateLibraryDependency(info)(project)
        doInSbtWriteCommandAction({
          seqCall.args.addExpr(addedExpr)
        }, seqCall.getContainingFile)
        Option(addedExpr)
      case _ => None
    }

  def addDependencyToFile(file: PsiFile, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    var addedExpr: PsiElement = null
    doInSbtWriteCommandAction({
      file.addAfter(generateNewLine(project), file.getLastChild)
      addedExpr = file.addAfter(generateLibraryDependency(info), file.getLastChild)
    }, file)
    addedExpr
  }

  def addDependencyToSettings(settings: ScMethodCall, info: ArtifactInfo)(implicit project: Project): PsiElement = {
    val addedExpr = generateLibraryDependency(info)(project)
    doInSbtWriteCommandAction({
      settings.args.addExpr(addedExpr)
    }, settings.getContainingFile)
    addedExpr
  }

  def isAddableLibraryDependencies(libDeps: ScInfixExpr): Boolean =
    libDeps.operation.refName match {
      case "+=" | "++=" => true
      case _ => false
    }

  private def doInSbtWriteCommandAction[T](f: => T, psiSbtFile: PsiFile): T =
    WriteCommandAction
      .writeCommandAction(psiSbtFile)
      .compute(() => f)

  private def generateSeqPsiMethodCall(implicit ctx: ProjectContext): ScMethodCall =
    ScalaPsiElementFactory.createElementFromText(s"$SEQ()").asInstanceOf[ScMethodCall]

  private def generateLibraryDependency(info: ArtifactInfo)(implicit ctx: ProjectContext): ScInfixExpr =
    ScalaPsiElementFactory.createElementFromText(s"$LIBRARY_DEPENDENCIES += ${generateArtifactText(info)}").asInstanceOf[ScInfixExpr]

  private def generateArtifactPsiExpression(info: ArtifactInfo)(implicit ctx: ProjectContext): ScExpression =
    ScalaPsiElementFactory.createElementFromText(generateArtifactText(info))(ctx).asInstanceOf[ScExpression]

  private def generateNewLine(implicit ctx: ProjectContext): PsiElement = ScalaPsiElementFactory.createElementFromText("\n")

  def generateArtifactText(info: ArtifactInfo): String =
    generateArtifactTextVerbose(info.groupId, info.artifactId, info.version, info.configuration)

  def generateArtifactTextVerbose(groupId: String, artifactId: String, version: String, configuration: String): String = {
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

  def getRelativePath(elem: PsiElement)(implicit project: ProjectContext): Option[String] = {
    for {
      path <- Option(elem.getContainingFile.getVirtualFile.getCanonicalPath)
      if path.startsWith(project.getBasePath)
    } yield
      path.substring(project.getBasePath.length + 1)
  }

  def toDependencyPlaceInfo(elem: PsiElement, affectedProjects: Seq[String])(implicit ctx: ProjectContext): Option[DependencyPlaceInfo] = {
    val offset =
      elem match {
        case call: ScMethodCall =>
          call.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression => expr.nameId.getTextOffset
            case _ => elem.getTextOffset
          }
        case _ => elem.getTextOffset
      }

    val line: Int = StringUtil.offsetToLineNumber(elem.getContainingFile.charSequence, offset) + 1

    getRelativePath(elem).map { relpath =>
      DependencyPlaceInfo(relpath, offset, line, elem, affectedProjects)
    }
  }

  def getSbtFileOpt(module: OpenapiModule): Option[VirtualFile] = {
    val project = module.getProject
    val sbtProj: File = getSbtModuleData(module) match {
      case Some(sbtModuleData: SbtModuleData) => new File(sbtModuleData.buildURI.toString.stripPrefix("file:"))
      case _ => null
    }
    if (sbtProj == null) return null
    val sbtFilePath = sbtProj / Sbt.BuildFile
    if (sbtFilePath.exists()) {
      val virtualSbtFilePath = LocalFileSystem.getInstance().findFileByPath(sbtFilePath.getAbsolutePath)
      Some(virtualSbtFilePath)
    }
    else { // get top level sbt file
      val baseDir: VirtualFile = project.baseDir
      val sbtFileOpt = baseDir.findChild(Sbt.BuildFile) match {
        case buildFile if buildFile != null && buildFile.exists() => Some(buildFile)
        case _ => baseDir.getChildren.find(language.SbtFileType.isMyFileType)
      }
      sbtFileOpt
    }
  }
}