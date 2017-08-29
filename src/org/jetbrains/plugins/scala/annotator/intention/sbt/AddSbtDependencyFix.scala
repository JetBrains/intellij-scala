package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.codeInsight.intention.{IntentionAction, LowPriorityAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.annotator.intention.sbt.AddSbtDependencyUtils._
import org.jetbrains.plugins.scala.annotator.intention.sbt.ui.SbtArtifactSearchWizard
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
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

    def getDependencyPlaces: Seq[DependencyPlaceInfo] = {
      var res: Seq[DependencyPlaceInfo] = List()

      val libDeps: Seq[ScInfixExpr] = getTopLevelLibraryDependencies(psiSbtFile)
      res ++= libDeps
        .map((elem: ScInfixExpr) => toFileLine(elem, Seq())(project))

      val sbtProjects: Seq[ScPatternDefinition] = getTopLevelSbtProjects(psiSbtFile)

      val moduleName = ModuleUtilCore.findModuleForPsiElement(refElement).getName

      val modules = ModuleManager.getInstance(project).getModules
      val projToAffectedModules = sbtProjects.map(proj => proj -> modules.map(_.getName).filter(containsModuleName(proj, _))).toMap

      val elemToAffectedProjects = collection.mutable.Map[PsiElement, Seq[String]]()
      sbtProjects.foreach(proj => {
        val places = getPossiblePlacesToAddFromProjectDefinition(proj)
        places.foreach(elem => {
          elemToAffectedProjects.update(elem, elemToAffectedProjects.getOrElse(elem, Seq()) ++ projToAffectedModules(proj))
        })
      })

      res ++= elemToAffectedProjects.toList
        .sortWith(_._2.toString < _._2.toString)
        .sortWith(_._2.contains(moduleName) && !_._2.contains(moduleName))
        .map(_._1)
        .map(elem => toFileLine(elem, elemToAffectedProjects(elem))(project))

      res ++= Seq(getTopLevelPlaceToAdd(psiSbtFile)(project))

      res.distinct
    }

    val foundFileLines = getDependencyPlaces

    val wizard = new SbtArtifactSearchWizard(project, artifactInfoSet, foundFileLines)

    val (infoOption, fileLineOption) = wizard.search()
    if (infoOption.isEmpty || fileLineOption.isEmpty)
      return

    val artifactInfo = infoOption.get
    val fileLine = fileLineOption.get

    addDependency(fileLine.element, artifactInfo)(project)

    refresh(project)
  }

  private def refresh(project: Project): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }

  private def containsModuleName(proj: ScPatternDefinition, moduleName: String): Boolean =
    proj.getText.contains("\"" + moduleName + "\"")

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
