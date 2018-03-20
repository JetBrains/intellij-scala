package org.jetbrains.sbt.annotator.dependency

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt.annotator.dependency.SbtDependenciesVisitor._
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by afonichkin on 7/21/17.
  */
object AddSbtDependencyUtils {
  val LIBRARY_DEPENDENCIES: String = "libraryDependencies"
  val SETTINGS: String = "settings"
  val SEQ: String = "Seq"

  val SBT_PROJECT_TYPE = "_root_.sbt.Project"
  val SBT_SEQ_TYPE = "_root_.scala.collection.Seq"
  val SBT_SETTING_TYPE = "_root_.sbt.Def.Setting"

  private val InfixOpsSet = Set(":=", "+=", "++=")

  def getPossiblePlacesToAddFromProjectDefinition(proj: ScPatternDefinition): Seq[PsiElement] = {
    var res: Seq[PsiElement] = List()

    def action(psiElement: PsiElement): Unit = {
      psiElement match {
        case e: ScInfixExpr if e.left.getText == LIBRARY_DEPENDENCIES && isAddableLibraryDependencies(e) => res ++= Seq(e)
        case call: ScMethodCall if call.deepestInvokedExpr.getText == SEQ => res ++= Seq(call)
        case typedSeq: ScTypedStmt if typedSeq.isSequenceArg =>
          typedSeq.expr match {
            case call: ScMethodCall if call.deepestInvokedExpr.getText == SEQ => res ++= Seq(typedSeq)
            case _ =>
          }
        case settings: ScMethodCall if isAddableSettings(settings) =>
          settings.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == SETTINGS => res ++= Seq(settings)
            case _ =>
          }
        case _ =>
      }
    }

    processPatternDefinition(proj)(action)

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
        if (infix.left.getText == LIBRARY_DEPENDENCIES && infix.getParent.isInstanceOf[PsiFile]) {
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
      case e: ScInfixExpr if e.left.getText == LIBRARY_DEPENDENCIES => addDependencyToLibraryDependencies(e, info)
      case call: ScMethodCall if call.deepestInvokedExpr.getText == SEQ => addDependencyToSeq(call, info)
      case typedSeq: ScTypedStmt if typedSeq.isSequenceArg => addDependencyToTypedSeq(typedSeq, info)
      case settings: ScMethodCall if isAddableSettings(settings) =>
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
        val seqCall: ScMethodCall = generateSeqPsiMethodCall(info)(project)

        doInSbtWriteCommandAction({
          seqCall.args.addExpr(dependency.copy().asInstanceOf[ScExpression])
          seqCall.args.addExpr(generateArtifactPsiExpression(info)(project))
          infix.operation.replace(ScalaPsiElementFactory.createElementFromText("++=")(project))
          dependency.replace(seqCall)
        }, psiFile)(project)

        Option(infix.right)

      case "++=" =>
        val dependencies: ScExpression = infix.right
        dependencies match {
          case call: ScMethodCall if call.deepestInvokedExpr.getText == SEQ=>
            val addedExpr = generateArtifactPsiExpression(info)(project)
            doInSbtWriteCommandAction(call.args.addExpr(addedExpr), psiFile)(project)
            Option(addedExpr)
          case _ => None
        }

      case _ => None
    }
  }

  def addDependencyToSeq(seqCall: ScMethodCall, info: ArtifactInfo)(implicit project: Project): Option[PsiElement] = {
    def isValid(expr: ScInfixExpr) = InfixOpsSet.contains(expr.operation.refName)
    val parentDef = Option(PsiTreeUtil.getParentOfType(seqCall, classOf[ScInfixExpr]))
    val addedExpr = parentDef match {
      case Some(expr) if isValid(expr) && expr.left.textMatches(LIBRARY_DEPENDENCIES) =>
        generateArtifactPsiExpression(info)
      case _ => generateLibraryDependency(info)
    }
    doInSbtWriteCommandAction(seqCall.args.addExpr(addedExpr), seqCall.getContainingFile)
    Some(addedExpr)
  }

  def addDependencyToTypedSeq(typedSeq: ScTypedStmt, info: ArtifactInfo)(implicit project: Project): Option[PsiElement] =
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
    }, settings.getContainingFile)(project)
    addedExpr
  }

  def isAddableSettings(settings: ScMethodCall): Boolean = {
    val args = settings.args.exprsArray

    if (args.length == 1) {
      args(0) match {
        case typedStmt: ScTypedStmt if typedStmt.isSequenceArg =>
          typedStmt.expr match {
            case _: ScMethodCall => false
            case _: ScReferenceExpression => false
            case _ => true
          }
        case _ => true
      }
    } else true
  }

  def isAddableLibraryDependencies(libDeps: ScInfixExpr): Boolean =
    libDeps.operation.refName match {
      case "+=" => true
      case "++=" =>  libDeps.right match {
        // In this case we return false to not repeat it several times
        case call: ScMethodCall if call.deepestInvokedExpr.getText == SEQ => false
        case _ => true
      }
      case _ => false
    }

  private def doInSbtWriteCommandAction[T](f: => T, psiSbtFile: PsiFile)(implicit project: ProjectContext): T =
    WriteCommandAction
      .writeCommandAction(psiSbtFile)
      .compute(() => f)

  private def generateSeqPsiMethodCall(info: ArtifactInfo)(implicit ctx: ProjectContext): ScMethodCall =
    ScalaPsiElementFactory.createElementFromText(s"$SEQ()").asInstanceOf[ScMethodCall]

  private def generateLibraryDependency(info: ArtifactInfo)(implicit ctx: ProjectContext): ScInfixExpr =
    ScalaPsiElementFactory.createElementFromText(s"$LIBRARY_DEPENDENCIES += ${generateArtifactText(info)}").asInstanceOf[ScInfixExpr]

  private def generateArtifactPsiExpression(info: ArtifactInfo)(implicit ctx: ProjectContext): ScExpression =
    ScalaPsiElementFactory.createElementFromText(generateArtifactText(info))(ctx).asInstanceOf[ScExpression]

  private def generateNewLine(implicit ctx: ProjectContext): PsiElement = ScalaPsiElementFactory.createElementFromText("\n")

  private def generateArtifactText(info: ArtifactInfo): String = {
    if (info.artifactId.matches("^.+_\\d+\\.\\d+$"))
      s""""${info.groupId}" %% "${info.artifactId.replaceAll("_\\d+\\.\\d+$", "")}" % "${info.version}""""
    else
      s""""${info.groupId}" % "${info.artifactId}" % "${info.version}""""
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

}
