package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.codeInsight.intention.{IntentionAction, LowPriorityAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.annotator.intention.sbt.ui.SbtArtifactSearchWizard
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.resolvers.SbtResolver

/**
  * Created by afonichkin on 7/7/17.
  */
class AddSbtDependencyFix(refElement: ScReferenceElement) extends IntentionAction with LowPriorityAction {
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

    def findFileLines(): Seq[FileLine] = {
      val libDeps: Seq[ScInfixExpr] = AddSbtDependencyUtils.getTopLevelLibraryDependencies(psiSbtFile)
      val fileLines = libDeps.map((elem: ScInfixExpr) => toFileLine(elem)(project))

      val sbtProjects: Seq[ScPatternDefinition] = AddSbtDependencyUtils.getTopLevelSbtProjects(psiSbtFile)
      val patternDefinitionToSettings = scala.collection.mutable.HashMap.empty[ScPatternDefinition, Set[ScMethodCall]]

      sbtProjects.foreach(sbtProj => {
        patternDefinitionToSettings.put(sbtProj, AddSbtDependencyUtils.getSettings(sbtProj).toSet)
      })

      val libDepsFromProjects: Iterable[ScInfixExpr] =
        patternDefinitionToSettings.values.flatMap(f => f.toList).flatMap(f => AddSbtDependencyUtils.getLibraryDepenciesInsideSettings(f))

      val seqCalls: Iterable[ScMethodCall] = libDepsFromProjects.flatMap(l => AddSbtDependencyUtils.getPossiblePlacesToAddFromLibraryDependencies(l))

      val x = 1

      fileLines ++
        libDepsFromProjects.map(f => toFileLine(f)(project)) ++
        seqCalls.map(f => toFileLine(f)(project)) ++
        patternDefinitionToSettings.values.flatten.map(f => toFileLine(f)(project))
    }

    val foundFileLines = findFileLines()

    val wizard = new SbtArtifactSearchWizard(project, artifactInfoSet, foundFileLines)

    val (infoOption, fileLineOption) = wizard.search()
    if (infoOption.isEmpty || fileLineOption.isEmpty)
      return

    val artifactInfo = infoOption.get
    val fileLine = fileLineOption.get

    AddSbtDependencyUtils.addDependency(fileLine.element, artifactInfo)(project)

    refresh(project)
  }

  private def toFileLine(elem: PsiElement)(implicit project: Project): FileLine = {
    val path = elem.getContainingFile.getVirtualFile.getCanonicalPath
    if (!path.startsWith(project.getBasePath))
      return null

    val relativePath = path.substring(project.getBasePath.length + 1)

    val offset =
      elem match {
        case call: ScMethodCall =>
          call.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression => expr.nameId.getTextOffset
            case _ => elem.getTextOffset
          }
        case _ => elem.getTextOffset
      }

    FileLine(relativePath, offset, elem)
  }

  private def refresh(project: Project): Unit = {
    // Do we need to refresh the project?
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }

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
