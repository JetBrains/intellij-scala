package org.jetbrains.plugins.scala.annotator.intention.sbt

import com.intellij.codeInsight.intention.{IntentionAction, LowPriorityAction}
import com.intellij.notification.{Notification, NotificationType}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{ModuleManager, ModuleUtilCore}
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiManager, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.annotator.intention.sbt.AddSbtDependencyUtils._
import org.jetbrains.plugins.scala.annotator.intention.sbt.ui.SbtArtifactSearchWizard
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.ValidSmartPointer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.resolvers.{ArtifactInfo, SbtResolver}
import org.jetbrains.sbt.settings.SbtSystemSettings

/**
  * Created by afonichkin on 7/7/17.
  */
class AddSbtDependencyFix(refElement: SmartPsiElementPointer[ScReferenceElement]) extends IntentionAction with LowPriorityAction {
  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file match {
    case _: ScalaCodeFragment => false
    case scalaFile: ScalaFile =>
      val isInProject = refElement match {
        case ValidSmartPointer(element) => element.getManager.isInProject(scalaFile)
        case _ => false
      }

      isInProject && !SbtSystemSettings.getInstance(project).getLinkedProjectsSettings.isEmpty
    case _ => false
  }

  override def getText: String = "Add sbt dependency..."

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val element = refElement.getElement

    def filterByScalaVer(artifacts: Set[ArtifactInfo]): Set[ArtifactInfo] = {
      val scalaVer = element.module.flatMap(_.scalaSdk.map(_.languageLevel.version))
      scalaVer
        .map(version => artifacts.filter(_.artifactId.endsWith(version)))
        .map(res => if (res.nonEmpty) res else artifacts)
        .getOrElse(artifacts)
    }

    val baseDir: VirtualFile = project.getBaseDir
    val sbtFileOpt: Option[VirtualFile] = {
      val buildSbt = baseDir.findChild(Sbt.BuildFile)
      if (buildSbt.exists())
        Some(buildSbt)
      else
        baseDir.getChildren.find(vf => Sbt.isSbtFile(vf.getPath))
    }

    val resolver: SbtResolver = SbtResolver.localCacheResolver(None)

    ProgressManager.getInstance().run(new Task.Modal(project, getText, true) {

      override def run(indicator: ProgressIndicator): Unit = {
        import com.intellij.notification.Notifications.Bus
        def error(msg: String): Unit = Bus.notify(new Notification(getText, getText, msg, NotificationType.ERROR))

        def getDeps: Set[ArtifactInfo] = {
          def doSearch(name: String): Set[ArtifactInfo] = resolver.getIndex(project)
            .map(_.searchArtifactInfo(name)(project))
            .getOrElse(Set.empty)

          indicator.setText("Searching for artifacts...")
          val fqName = extensions.inReadAction(getReferenceText)
          val artifactInfoSet = if (fqName.endsWith("._")) { // search wildcard imports by containing package
            doSearch(fqName.replaceAll("_$", "")) match {
              case set if set.isEmpty => doSearch(fqName.replaceAll("._$", "")) // not a package, try searching for a class
              case result => result
            }
          } else {
            doSearch(fqName) match {
              case set if set.isEmpty && fqName.contains(".") => // imported name is not a class -> search for enclosing package
                doSearch(fqName.substring(0, fqName.lastIndexOf(".") + 1))
              case result => result
            }
          }
          extensions.inReadAction(filterByScalaVer(artifactInfoSet))
        }

        def getPlaces: Seq[DependencyPlaceInfo] = {
          indicator.setText("Finding SBT dependency declarations...")
          val depPlaces = extensions.inReadAction(
            for {
              sbtFile <- sbtFileOpt
              psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
              depPlaces = getDependencyPlaces(project, psiSbtFile)
            } yield depPlaces
          )
          depPlaces.getOrElse(Seq.empty)
        }

        indicator.setIndeterminate(true)

        resolver.getIndex(project).foreach {
          indicator.setText("Updating dependency index...")
          _.doUpdate(Some(indicator))(project)
        }

        val deps = getDeps
        if (deps.isEmpty) {
          error("No dependencies found for given import")
          return
        }

        val places = getPlaces
        if (places.isEmpty) {
          error("No places to add a dependency found")
          return
        }

        ApplicationManager.getApplication.invokeLater { () =>
          val wizard = new SbtArtifactSearchWizard(project, deps, places)
          wizard.search() match {
            case (Some(artifactInfo), Some(fileLine)) =>
              addDependency(fileLine.element, artifactInfo)(project)
              refresh(project)
            case _ =>
          }
        }
      }
    })
  }

  private def getDependencyPlaces(project: Project, psiSbtFile: ScalaFile): Seq[DependencyPlaceInfo] = {
    var res: List[DependencyPlaceInfo] = List()

    val libDeps: Seq[ScInfixExpr] = getTopLevelLibraryDependencies(psiSbtFile)
    res ++= libDeps
      .flatMap((elem: ScInfixExpr) => toDependencyPlaceInfo(elem, Seq())(project))

    val sbtProjects: Seq[ScPatternDefinition] = getTopLevelSbtProjects(psiSbtFile)

    val moduleName = ModuleUtilCore.findModuleForPsiElement(refElement.getElement).getName

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
      .flatMap(elem => toDependencyPlaceInfo(elem, elemToAffectedProjects(elem))(project))

    res ++= getTopLevelPlaceToAdd(psiSbtFile)(project).toList

    res.distinct
  }

  private def refresh(project: Project): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }

  private def containsModuleName(proj: ScPatternDefinition, moduleName: String): Boolean =
    proj.getText.contains("\"" + moduleName + "\"")

  private def getReferenceText: String = {
    val importExpr = PsiTreeUtil.getParentOfType(refElement.getElement, classOf[ScImportExpr])
    if (importExpr.selectors.size > 1 || importExpr.selectors.exists(_.isAliasedImport))
      s"${importExpr.qualifier.getText}.${refElement.getElement.getText}" // for "import x.y.{foo, bar=>baz}" and so on
    else
      importExpr.getText
  }

  override def getFamilyName = "Add sbt dependencies"

  override def startInWriteAction(): Boolean = false


}
