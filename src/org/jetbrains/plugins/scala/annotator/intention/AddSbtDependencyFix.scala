package org.jetbrains.plugins.scala.annotator.intention

import com.intellij.codeInsight.intention.{IntentionAction, LowPriorityAction}
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.annotator.intention.ui.SbtArtifactSearchDialog
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.resolvers.{ArtifactInfo, SbtResolver}

/**
  * Created by afonichkin on 7/7/17.
  */
class AddSbtDependencyFix(refElement: ScReferenceElement) extends IntentionAction with LowPriorityAction {
  val libraryDependencies: String = "libraryDependencies"

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

  override def getText: String = "Add SBT dependency..."

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val baseDir: VirtualFile = project.getBaseDir
    val sbtFile: VirtualFile = baseDir.findChild(Sbt.BuildFile)

    if (!sbtFile.exists())
      return

    val psiSbtFile: ScalaFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]

    val resolver: SbtResolver = SbtResolver.localCacheResolver(None)
    val ivyIndex = resolver.getIndex(project)
    // For the case when refElement.refName is fully quialified name
    val artifactInfoSet = ivyIndex.searchArtifactInfo(getReferenceText)

    // Can't make any suggestions
    if (artifactInfoSet.isEmpty)
      return

    val dialog = new SbtArtifactSearchDialog(project, true, artifactInfoSet)
    val infoOption: Option[ArtifactInfo] = dialog.searchForArtifact()
    if (infoOption.isEmpty)
      return

    val artifactInfo = infoOption.get

    // Basic case when libraryDependencies on the top level of build.sbt file
    psiSbtFile.acceptChildren(new ScalaElementVisitor {
      override def visitInfixExpression(infix: ScInfixExpr): Unit = {
        // Looking for the top level libraryDependencies element
        if (infix.lOp.getText == libraryDependencies && infix.getParent.isInstanceOf[PsiFile]) {
          processLibraryDependencies(infix, artifactInfo, psiSbtFile)(project)
        }
      }
    })

    // Do we need to refresh the project?
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }

  private def processLibraryDependencies(infix: ScInfixExpr, info: ArtifactInfo, psiSbtFile: PsiFile)(implicit project: Project) = {
    val opName = infix.operation.refName

    if (opName == "+=") {
      val dependency: ScExpression = infix.rOp
      val seqCall: ScMethodCall = generateSeqPsiMethodCall(info)(project)

      doInSbtWriteCommandAction({
        seqCall.args.addExpr(dependency.copy().asInstanceOf[ScExpression])
        seqCall.args.addExpr(generateArtifactPsiExpression(info)(project))
        infix.operation.replace(ScalaPsiElementFactory.createElementFromText("++=")(project))
        dependency.replace(seqCall)
      }, psiSbtFile)(project)
    } else if (opName == "++=") {
      val dependencies: ScExpression = infix.rOp
      dependencies match {
        case call: ScMethodCall =>
          val text = call.deepestInvokedExpr.getText
          // TODO: Add support for more other collections
          if (text == "Seq") {
            doInSbtWriteCommandAction({
              call.args.addExpr(generateArtifactPsiExpression(info)(project))
            }, psiSbtFile)(project)
          }
        case _ =>
      }
    }
  }

  private def doInSbtWriteCommandAction(f: => Unit, psiSbtFile: PsiFile)(implicit project: ProjectContext): Unit = {
    new WriteCommandAction[Unit](project, psiSbtFile) {
      override def run(result: Result[Unit]): Unit = {
        f
      }
    }.execute()
  }

  private def generateSeqPsiMethodCall(info: ArtifactInfo)(implicit ctx: ProjectContext): ScMethodCall =
    ScalaPsiElementFactory.createElementFromText("Seq()").asInstanceOf[ScMethodCall]

  private def generateArtifactPsiExpression(info: ArtifactInfo)(implicit ctx: ProjectContext): ScExpression =
    ScalaPsiElementFactory.createElementFromText(generateArtifactText(info))(ctx).asInstanceOf[ScExpression]

  private def generateArtifactText(info: ArtifactInfo): String =
    "\"" + s"${info.groupId}" + "\" % \"" + s"${info.artifactId}" + "\" % \"" + s"${info.version}" + "\""

  private def getReferenceText: String = {
    var result = refElement
    while (result.getParent.isInstanceOf[ScReferenceElement]) {
      result = result.getParent.asInstanceOf[ScReferenceElement]
    }

    result.getText
  }

  override def getFamilyName = "Add SBT dependencies"

  override def startInWriteAction(): Boolean = false
}
