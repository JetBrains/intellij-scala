package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.libraries.{LibraryTable, LibraryTablesRegistrar}
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle

class CreateImportedLibraryQuickFix(myPsi: PsiElement) extends LocalQuickFixOnPsiElement(myPsi) {
  override def getText: String = WorksheetBundle.message("ammonite.create.library.from.jar")

  override def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    def selectLibName(stableExpr: ScStableCodeReference, table: LibraryTable): String = {
      val refName = AmmoniteUtil.extractLibInfo(stableExpr).map(AmmoniteUtil.convertLibName).getOrElse(stableExpr.refName)
      var name = refName
      var count = 2

      while (table.getLibraryByName(name) != null) { //weird, but possible
        name = s"$refName ($count)"
        count += 1
      }

      name
    }

    startElement match {
      case stableExpr: ScStableCodeReference =>
        AmmoniteUtil.findJarRoot(stableExpr).foreach {
          jarRoot =>
            extensions.inWriteAction {
              val libTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
              val tableModel = libTable.getModifiableModel
              val lib = tableModel.createLibrary(selectLibName(stableExpr, libTable))

              val model = lib.getModifiableModel
              model.addRoot(jarRoot, OrderRootType.CLASSES)

              model.commit()
              tableModel.commit()

              Option(file.getVirtualFile).flatMap(ScalaUtil.getModuleForFile(_)(project)).foreach {
                module =>
                  val moduleModel = ModuleRootManager.getInstance(module).getModifiableModel
                  moduleModel.addLibraryEntry(lib)
                  moduleModel.commit()

                  Option(ProjectStructureConfigurable.getInstance(project).getModulesConfig.getContext).foreach (
                    context => Option(context.getDaemonAnalyzer).foreach(
                      _.queueUpdate(new ModuleProjectStructureElement(context, module))
                    )
                  )
              }
            }

        }
      case _ =>
    }
  }

  //noinspection ScalaExtractStringToBundle
  override def getFamilyName: String = "Scala"
}
