package org.jetbrains.sbt.language.utils

import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{ModuleManager, Module => OpenapiModule}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiFileExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt, ProjectPsiElementExt}
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.SbtUtil.{getBuildModuleData, getSbtModuleData}
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.GetDep

object SbtDependencyUtils {
  val LIBRARY_DEPENDENCIES: String = "libraryDependencies"
  val SETTINGS: String = "settings"
  val SEQ: String = "Seq"
  val ANY: String = "Any"

  val SBT_PROJECT_TYPE = "_root_.sbt.Project"
  val SBT_SETTING_TYPE = "_root_.sbt.Def.Setting"
  val SBT_MODULE_ID_TYPE = "sbt.ModuleID"
  val SBT_LIB_CONFIGURATION = "_root_.sbt.librarymanagement.Configuration"

  val SCALA_DEPENDENCIES_WITH_MINOR_SCALA_VERSION_LIST = List(
    "ch.epfl.scala:scalafix-cli",
    "ch.epfl.scala:scalafix-reflect",
    "ch.epfl.scala:scalafix-testkit",
    "com.avsystem.scex:scex-core",
    "com.avsystem.scex:scex-macros",
    "com.avsystem.scex:scex-util",
    "com.github.cb372:scala-typed-holes",
    "com.github.dkhalansky:paradisenglib",
    "com.github.dkhalansky:paradisengplugin",
    "com.github.ghik:silencer-lib",
    "com.github.ghik:silencer-plugin",
    "com.github.tomasmikula:pascal",
    "com.github.wheaties:twotails",
    "com.github.wheaties:twotails-annotations",
    "com.kubukoz:better-tostring",
    "com.lihaoyi:ammonite",
    "com.lihaoyi:ammonite-api",
    "com.lihaoyi:ammonite-compiler",
    "com.lihaoyi:ammonite-compiler-interface",
    "com.lihaoyi:ammonite-interp",
    "com.lihaoyi:ammonite-interp-api",
    "com.lihaoyi:ammonite-interpApi",
    "com.lihaoyi:ammonite-repl",
    "com.lihaoyi:ammonite-repl-api",
    "com.lihaoyi:ammonite-replApi",
    "com.lihaoyi:ammonite-runtime",
    "com.lihaoyi:ammonite-shell",
    "com.lihaoyi:ammonite-sshd",
    "com.lihaoyi:ammonite-test-api",
    "com.lihaoyi:ammonite-util",
    "com.lihaoyi:mill-bridge",
    "com.sksamuel.scapegoat:scalac-scapegoat-plugin",
    "com.typesafe.genjavadoc:genjavadoc-plugin",
    "edu.berkeley.cs:chisel3-plugin",
    "io.methvin:orphan-finder",
    "io.regadas:socco-ng",
    "io.tryp:splain",
    "org.jupyter-scala:ammonite",
    "org.jupyter-scala:ammonite-compiler",
    "org.jupyter-scala:ammonite-repl",
    "org.jupyter-scala:ammonite-runtime",
    "org.jupyter-scala:ammonite-sshd",
    "org.jupyter-scala:ammonite-util",
    "org.jupyter-scala:scala-api",
    "org.jupyter-scala:scala-cli",
    "org.jupyter-scala:scala-kernel",
    "org.scala-js:scalajs-compiler",
    "org.scala-js:scalajs-junit-test-plugin",
    "org.scala-lang.plugins:scala-continuations-plugin",
    "org.scala-native:junit-plugin",
    "org.scala-native:nscplugin",
    "org.scala-refactoring:org.scala-refactoring.library",
    "org.scala-sbt.sxr:sxr",
    "org.scalamacros:paradise",
    "org.scalameta:interactive",
    "org.scalameta:metac",
    "org.scalameta:mtags",
    "org.scalameta:paradise",
    "org.scalameta:scalahost",
    "org.scalameta:scalahost-nsc",
    "org.scalameta:semanticdb-scalac",
    "org.scalameta:semanticdb-scalac-core",
    "org.scalaz:deriving-plugin",
    "org.scoverage:scalac-scoverage-plugin",
    "org.scoverage:scalac-scoverage-runtime",
    "org.scoverage:scalac-scoverage-runtime_sjs1",
    "org.typelevel:kind-projector",
    "org.virtuslab.semanticgraphs:scalac-plugin",
    "org.wartremover:wartremover",
    "org.wartremover:wartremover-contrib",
    "sh.almond:scala-interpreter",
    "sh.almond:scala-kernel",
    "sh.almond:scala-kernel-api"
  )

  sealed trait GetMode
  object GetMode {
    case object GetPlace extends GetMode
    case object GetDep extends GetMode
  }

  /** Use [[org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil.getAllScalaVersionsOrDefault]] */
  @ApiStatus.Obsolete
  def getAllScalaVersionsOrDefault(psiElement: PsiElement, majorOnly: Boolean = false): Seq[String] = {
    var scalaVers = psiElement.getProject.allScalaVersions.map(_.minor).sortWith(SbtDependencyUtils.isGreaterStableVersion)
    if (scalaVers.isEmpty) scalaVers = Seq(psiElement.scalaLanguageLevelOrDefault.getVersion)
    if (majorOnly) scalaVers = scalaVers.map(ver => ver.split("\\.").take(2).mkString("."))
    scalaVers.distinct
  }

  def preprocessVersion(v: String): String = {
    val pattern = """\d+""".r
    v.split("\\.").map(part => {
      val newPart = pattern.findAllIn(part).toList
      if (newPart.isEmpty) 0
      else newPart(0)
    }).mkString(".")
  }

  def isVersionStable(version: String): Boolean = {
    val unstablePattern = """.*[a-zA-Z-].*"""
    !version.matches(unstablePattern)
  }

  def isGreaterStableVersion(newVer: String, oldVer: String): Boolean = {
    val oldVerPreprocessed = preprocessVersion(oldVer)
    val newVerPreprocessed = preprocessVersion(newVer)
    newVerPreprocessed.split("\\.")
      .zipAll(oldVerPreprocessed.split("\\."), "0", "0")
      .find {case(a, b) => a != b }
      .fold(0) { case (a, b) => a.toInt - b.toInt } > 0
  }

  /** Use [[org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil.buildScalaArtifactIdString]] */
  @ApiStatus.Obsolete
  def buildScalaArtifactIdString(groupId: String, artifactId: String, scalaVer: String): String = {
    if (scalaVer == null || scalaVer.isEmpty) return artifactId
    val shouldIncludeScalaMinorVer = SCALA_DEPENDENCIES_WITH_MINOR_SCALA_VERSION_LIST contains s"$groupId:$artifactId"
    val paddedScalaVer = scalaVer.split('.').padTo(3, "0")
    if (shouldIncludeScalaMinorVer) return s"${artifactId}_${paddedScalaVer.mkString(".")}"
    paddedScalaVer.head match {
      case "3" =>
        s"${artifactId}_${paddedScalaVer(0)}"
      case _ =>
        s"${artifactId}_${paddedScalaVer(0)}.${paddedScalaVer(1)}"
    }
  }

  def findLibraryDependency(project: Project,
                            module: OpenapiModule,
                            dependency: UnifiedDependency,
                            versionRequired: Boolean = true,
                            configurationRequired: Boolean = true): (ScInfixExpr, String, ScInfixExpr) = {
    val sbtFileOpt = getSbtFileOpt(module)
    val targetCoordinates = dependency.getCoordinates
    val targetDepText: String = generateArtifactTextVerbose(
      targetCoordinates.getGroupId,
      targetCoordinates.getArtifactId.replaceAll("_\\d+.*$", ""),
      if (versionRequired) targetCoordinates.getVersion else "",
      if (configurationRequired) dependency.getScope else SbtDependencyCommon.defaultLibScope)
    val libDeps = getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, GetDep)
    libDeps.foreach(
      libDep => {
        var processedDep: List[String] = List()
        processedDep = processLibraryDependencyFromExprAndString(libDep.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)]).map(_.asInstanceOf[String])
        var processedDepText: String = ""
        processedDep match {
          case List(a, b, c) =>
            processedDepText = generateArtifactTextVerbose(
              a,
              b,
              if (versionRequired) c else "",
              SbtDependencyCommon.defaultLibScope)
          case List(a, b, c, d) =>
            processedDepText = generateArtifactTextVerbose(
              a,
              b,
              if (versionRequired) c else "",
              if (configurationRequired) d else SbtDependencyCommon.defaultLibScope)
          case _ =>
        }

        if (targetDepText.equals(processedDepText)) {
          return libDep.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)]
        }
      }
    )
    null
  }


  def getLibraryDependenciesOrPlacesUtil(module: OpenapiModule,
                                         psiSbtFile: ScalaFile,
                                         mode: GetMode): Seq[(PsiElement, String, PsiElement)] = {
    var res: Seq[(PsiElement, String, PsiElement)] = Seq()
    val sbtFileModule = psiSbtFile.module.orNull
    if (sbtFileModule != null && (sbtFileModule == module || sbtFileModule.getName == s"""${module.getName}-build"""))
      res ++= getTopLevelLibraryDependencies(psiSbtFile).flatMap(
        libDep => getLibraryDependenciesOrPlacesFromPsi(libDep, mode))

    def containsModuleName(proj: ScPatternDefinition, moduleName: String): Boolean =
      proj.getText.toLowerCase.contains("\"" + moduleName + "\"".toLowerCase)

    val sbtProjectsInModule = getTopLevelSbtProjects(psiSbtFile).filter(proj => containsModuleName(proj, module.getName))

    res ++= sbtProjectsInModule.
      flatMap(proj => getPossiblePsiFromProjectDefinition(proj)).
      flatMap(elem => getLibraryDependenciesOrPlacesFromPsi(elem, mode))

    res.distinct
  }

  def getLibraryDependenciesOrPlaces(sbtFileOpt: Option[VirtualFile],
                                     project: Project,
                                     module: OpenapiModule,
                                     mode: GetMode): Seq[(PsiElement, String, PsiElement)] = {

    val libDeps = inReadAction(
      for {
        sbtFile <- sbtFileOpt
        psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
        deps = getLibraryDependenciesOrPlacesUtil(module, psiSbtFile, mode)
      } yield deps
    )
    libDeps.getOrElse(Seq.empty)
  }

  def processLibraryDependencyFromExprAndString(elem: (ScExpression, String, ScExpression), preserve: Boolean = false): List[Any] = {
    var res: List[Any] = List()

    def callbackInfix(psiElement: PsiElement): Boolean = {
      psiElement match {
        case stringLiteral: ScStringLiteral =>
          if (preserve) {
            res = stringLiteral :: res
          } else {
            res = cleanUpDependencyPart(stringLiteral.getText) :: res
          }
        case ref: ScReferenceExpression if ref.`type`().getOrAny.canonicalText.equals(SBT_LIB_CONFIGURATION) =>
          if (preserve) {
            res = ref :: res
          } else {
            res = cleanUpDependencyPart(ref.getText) :: res
          }
        case _ =>
      }
      true
    }

    elem._1 match {
      case infix: ScInfixExpr =>
        SbtDependencyTraverser.traverseInfixExpr(infix)(callbackInfix)
      case ref: ScReferenceExpression =>
        var infix: ScInfixExpr = null

        def callbackRef(psiElement: PsiElement): Boolean = {
          psiElement match {
            case subInfix: ScInfixExpr if subInfix.operation.refName.contains("%") =>
              infix = subInfix
              return false
            case _ =>
          }
          true
        }

        SbtDependencyTraverser.traverseReferenceExpr(ref)(callbackRef)
        SbtDependencyTraverser.traverseInfixExpr(infix)(callbackInfix)
    }


    elem._2 match {
      case s if s.nonEmpty => res = cleanUpDependencyPart(s) :: res
      case _ =>
    }
    res.reverse
  }

  def cleanUpDependencyPart(s: String): String = s.trim.replaceAll("^\"|\"$", "")

  def isScalaLibraryDependency(psi: PsiElement): Boolean = {
    var result = false

    def callback(psiElement: PsiElement): Boolean = {
      psiElement match {
        case infix: ScInfixExpr if infix.operation.refName.contains("%%") =>
          result = true
          false
        case _ => true
      }
    }

    inReadAction({
      psi match {
        case infix: ScInfixExpr => SbtDependencyTraverser.traverseInfixExpr(infix)(callback)
        case call: ScMethodCall => SbtDependencyTraverser.traverseMethodCall(call)(callback)
        case refExpr: ScReferenceExpression => SbtDependencyTraverser.traverseReferenceExpr(refExpr)(callback)
        case _ =>
      }
    })

    result
  }

  /** Parse Library Dependencies or Places from PsiElement
   *
   * @param psi  psiElement need passing
   * @param mode whether you want the library dependencies or places to add dependencies from the PsiElement
   * @return A sequence of tuple (PsiElement, String, PsiElement) where
   *         the first element is the PsiElement of the library dependencies/places
   *         the second element is the configuration string (Lib mode)
   *         the third element is the parent PsiElement that contains library dependency and its configuration (Lib mode)
   */
  def getLibraryDependenciesOrPlacesFromPsi(psi: PsiElement, mode: GetMode): Seq[(PsiElement, String, PsiElement)] = {
    var result: Seq[(PsiElement, String, PsiElement)] = List()

    def callbackDep(psiElement: PsiElement): Boolean = {
      psiElement match {
        case infix: ScInfixExpr if infix.operation.refName.contains("%") =>
          infix.getText.split('%').map(_.trim).filter(_.nonEmpty).length - 1 match {
            case 1 if infix.right.isInstanceOf[ScReferenceExpression] &&
              infix.right.`type`().getOrAny.canonicalText.equals(SBT_LIB_CONFIGURATION) => inReadAction {
              val configuration = cleanUpDependencyPart(infix.right.getText).toLowerCase.capitalize

              def callbackRef(psiElement: PsiElement): Boolean = {
                psiElement match {
                  case subInfix: ScInfixExpr if subInfix.operation.refName.contains("%") =>
                    result ++= Seq((subInfix, configuration, infix))
                    return false
                  case _ =>
                }
                true
              }

              infix.left match {
                case refExpr: ScReferenceExpression =>
                  SbtDependencyTraverser.traverseReferenceExpr(refExpr)(callbackRef)
                case _ =>
              }
              return false
            }
            case _ if infix.right.isInstanceOf[ScReferenceExpression] &&
              infix.right.`type`().getOrAny.canonicalText.equals(SBT_LIB_CONFIGURATION) =>
              val configuration = cleanUpDependencyPart(infix.right.getText).toLowerCase.capitalize
              result ++= Seq((infix.left, configuration, infix))
              return false
            case _ =>
              result ++= Seq((infix, "", infix))
              return false
          }
        case ref: ScReferenceExpression if ref.`type`().getOrAny.canonicalText.equals(SBT_MODULE_ID_TYPE) =>
          result ++= Seq((ref, "", ref))
          return false
        case _ =>
      }
      true
    }

    def callbackPlace(psiElement: PsiElement): Boolean = {
      psiElement match {
        case libDep: ScInfixExpr if libDep.left.textMatches(LIBRARY_DEPENDENCIES) & isAddableLibraryDependencies(libDep) =>
          result ++= Seq((libDep, "", libDep))
        case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) =>
          result ++= Seq((call, "", call))
        case settings: ScMethodCall =>
          settings.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == SETTINGS => result ++= Seq((settings, "", settings))
            case _ =>
          }
        case _ =>
      }
      true
    }

    def callback(psiElement: PsiElement): Boolean = {
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

  def getPossiblePsiFromProjectDefinition(proj: ScPatternDefinition): Seq[PsiElement] = {
    var res: Seq[PsiElement] = List()

    def action(psiElement: PsiElement): Boolean = {
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
      true
    }

    SbtDependencyTraverser.traversePatternDef(proj)(action)

    res
  }

  def getTopLevelSbtProjects(psiSbtFile: ScalaFile): Seq[ScPatternDefinition] = {
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

  def getTopLevelPlaceToAdd(psiFile: ScalaFile)(implicit project: Project): Option[DependencyOrRepositoryPlaceInfo] = {
    val line: Int = StringUtil.offsetToLineNumber(psiFile.charSequence, psiFile.getTextLength) + 1
    getRelativePath(psiFile).map { relpath =>
      DependencyOrRepositoryPlaceInfo(relpath, psiFile.getTextLength, line, psiFile, Seq())
    }
  }

  def addDependency(expr: PsiElement, info: SbtArtifactInfo)(implicit project: Project): Option[PsiElement] = {
    expr match {
      case e: ScInfixExpr if e.left.textMatches(LIBRARY_DEPENDENCIES) => addDependencyToLibraryDependencies(e, info)
      case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) => addDependencyToSeq(call, info)
      case typedSeq: ScTypedExpression if typedSeq.isSequenceArg => addDependencyToTypedSeq(typedSeq, info)
      case settings: ScMethodCall =>
        settings.getEffectiveInvokedExpr match {
          case expr: ScReferenceExpression if expr.refName == SETTINGS =>
            Option(addDependencyToSettings(settings, info))
          case _ => None
        }
      case file: PsiFile =>
        Option(addDependencyToFile(file, info)(project))
      case _ => None
    }
  }

  def addRepository(expr: PsiElement, unifiedDependencyRepository: UnifiedDependencyRepository)(implicit project: Project): Option[PsiElement] = {
    expr match {
      case file: PsiFile =>
        Option(addRepositoryToFile(file, unifiedDependencyRepository)(project))
      case _ => None
    }
  }

  def addDependencyToLibraryDependencies(infix: ScInfixExpr, info: SbtArtifactInfo)(implicit project: Project): Option[PsiElement] = {

    val psiFile = infix.getContainingFile

    infix.operation.refName match {
      case "+=" =>
        val dependency: ScExpression = infix.right
        val seqCall: ScMethodCall = generateSeqPsiMethodCall(infix)

        doInSbtWriteCommandAction({
          seqCall.args.addExpr(dependency.copy().asInstanceOf[ScExpression])
          seqCall.args.addExpr(generateArtifactPsiExpression(info, infix))
          infix.operation.replace(ScalaPsiElementFactory.createElementFromText("++=", infix)(project))
          dependency.replace(seqCall)
        }, psiFile)

        Option(infix.right)

      case "++=" =>
        val dependencies: ScExpression = infix.right
        dependencies match {
          case call: ScMethodCall if call.deepestInvokedExpr.textMatches(SEQ) =>
            val addedExpr = generateArtifactPsiExpression(info, call)
            doInSbtWriteCommandAction(call.args.addExpr(addedExpr), psiFile)
            Option(addedExpr)
          case subInfix: ScInfixExpr if subInfix.operation.refName == "++" =>
            doInSbtWriteCommandAction(
              subInfix.replace(
                ScalaPsiElementFactory.createExpressionFromText(
                  s"${subInfix.getText} ++ Seq(${generateArtifactText(info)})",
                  infix
                )(project)),
              psiFile
            )
            Option(infix.right)
          case _ => None
        }

      case _ => None
    }
  }

  def addDependencyToSeq(seqCall: ScMethodCall, info: SbtArtifactInfo)(implicit project: Project): Option[PsiElement] = {
    val addedExpr =
      if (!seqCall.`type`().getOrAny.canonicalText.contains(SBT_SETTING_TYPE))
        generateArtifactPsiExpression(info, seqCall)
      else generateLibraryDependency(info, seqCall)
    doInSbtWriteCommandAction(seqCall.args.addExpr(addedExpr), seqCall.getContainingFile)
    Some(addedExpr)
  }

  def addDependencyToTypedSeq(typedSeq: ScTypedExpression, info: SbtArtifactInfo): Option[PsiElement] =
    typedSeq.expr match {
      case seqCall: ScMethodCall =>
        val addedExpr = generateLibraryDependency(info, typedSeq)
        doInSbtWriteCommandAction({
          seqCall.args.addExpr(addedExpr)
        }, seqCall.getContainingFile)
        Option(addedExpr)
      case _ => None
    }

  def addDependencyToFile(file: PsiFile, info: SbtArtifactInfo)(implicit project: Project): PsiElement = {
    var addedExpr: PsiElement = null
    doInSbtWriteCommandAction({
      file.addAfter(generateNewLine(project), file.getLastChild)
      addedExpr = file.addAfter(generateLibraryDependency(info, file), file.getLastChild)
    }, file)
    addedExpr
  }

  def addDependencyToSettings(settings: ScMethodCall, info: SbtArtifactInfo): PsiElement = {
    val addedExpr = generateLibraryDependency(info, settings)
    doInSbtWriteCommandAction({
      settings.args.addExpr(addedExpr)
    }, settings.getContainingFile)
    addedExpr
  }

  def addRepositoryToFile(file: PsiFile, unifiedDependencyRepository: UnifiedDependencyRepository)(implicit project: Project): PsiElement = {
    var addedExpr: PsiElement = null
    doInSbtWriteCommandAction({
      file.addAfter(generateNewLine(project), file.getLastChild)
      addedExpr = file.addAfter(generateResolverPsiExpression(unifiedDependencyRepository, file), file.getLastChild)
    }, file)
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

  private def generateSeqPsiMethodCall(ctx: PsiElement): ScMethodCall =
    ScalaPsiElementFactory.createElementFromText[ScMethodCall](s"$SEQ()", ctx)(ctx)

  private def generateLibraryDependency(info: SbtArtifactInfo, ctx: PsiElement): ScInfixExpr =
    ScalaPsiElementFactory.createElementFromText[ScInfixExpr](
      s"$LIBRARY_DEPENDENCIES += ${generateArtifactText(info)}",
      ctx
    )(ctx)

   def generateArtifactPsiExpression(info: SbtArtifactInfo, ctx: PsiElement): ScExpression =
    ScalaPsiElementFactory.createElementFromText[ScExpression](
      generateArtifactText(info),
      ctx
    )(ctx)

  private def generateNewLine(implicit ctx: ProjectContext): PsiElement =
    ScalaPsiElementFactory.createNewLine()

  def generateArtifactText(info: SbtArtifactInfo): String =
    generateArtifactTextVerbose(info.groupId, info.artifactId, info.version, info.configuration)

  def generateArtifactTextVerbose(groupId: String, artifactId: String, version: String, configuration: String): String = {
    var artifactText = ""
    if (artifactId.matches("^.+_\\d+.*$"))
      artifactText += s""""${groupId}" %% "${artifactId.replaceAll("_\\d+.*$", "")}" % "${version}""""
    else
      artifactText += s""""${groupId}" % "${artifactId}" % "${version}""""

    if (configuration != SbtDependencyCommon.defaultLibScope) {
      artifactText += s""" % $configuration"""
    }
    artifactText
  }

  def generateResolverText(unifiedDependencyRepository: UnifiedDependencyRepository): String =
    s"""resolvers += Resolver.url("${unifiedDependencyRepository.getId}", url("${unifiedDependencyRepository.getUrl}"))"""

  def generateResolverPsiExpression(unifiedDependencyRepository: UnifiedDependencyRepository, ctx: PsiElement): ScExpression =
    ScalaPsiElementFactory.createExpressionFromText(generateResolverText(unifiedDependencyRepository), ctx)(ctx)

  def getRelativePath(elem: PsiElement)(implicit project: ProjectContext): Option[String] = {
    for {
      path <- Option(elem.getContainingFile.getVirtualFile.getCanonicalPath)
      if path.startsWith(project.getBasePath)
    } yield
      path.substring(project.getBasePath.length + 1)
  }

  def toDependencyPlaceInfo(elem: PsiElement, affectedProjects: Seq[String])(implicit ctx: ProjectContext): Option[DependencyOrRepositoryPlaceInfo] = {
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
      DependencyOrRepositoryPlaceInfo(relpath, offset, line, elem, affectedProjects)
    }
  }

  def getSbtFileFromBuildModule(buildModule: OpenapiModule): Option[VirtualFile] = {
    val buildSbt =  ModuleRootManager.getInstance(buildModule)
      .getContentRoots
      .flatMap(f => f.getParent.findChild(Sbt.BuildFile).toOption)

    val otherSbtFiles = ModuleRootManager.getInstance(buildModule)
      .getContentRoots
      .flatMap(f => f.getChildren.filter(_.getName.endsWith(Sbt.Extension)))

    buildSbt.headOption
      .orElse(otherSbtFiles.find(_.getName == Sbt.BuildFile))
      .orElse(otherSbtFiles.headOption)
  }

  /** Find the corresponding sbt build module for an sbt module */
  def getBuildModule(module: OpenapiModule): Option[OpenapiModule] = {
    val project = module.getProject
    val moduleManager = ModuleManager.getInstance(project)
    val buildModules = for {
      moduleData <- getSbtModuleData(module).to(Seq)
      m <- moduleManager.getModules
      sbtModuleData <- getBuildModuleData(project, m)
      if moduleData.buildURI == sbtModuleData.buildFor
    } yield m
    buildModules.headOption // we only expect zero or one here
  }

  def getSbtFileOpt(module: OpenapiModule): Option[VirtualFile] =
    getBuildModule(module)
      .flatMap(getSbtFileFromBuildModule)
      .orElse(getSbtFileFromBuildModule(module)) // if module is itself a build module
}
