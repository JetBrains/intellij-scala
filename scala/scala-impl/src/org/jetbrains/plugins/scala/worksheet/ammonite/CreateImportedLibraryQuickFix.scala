package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.util.ScalaUtil

/**
  * User: Dmitry.Naydanov
  * Date: 29.08.17.
  */
class CreateImportedLibraryQuickFix(private val myPsi: PsiElement) extends LocalQuickFixOnPsiElement(myPsi) {
  override def getText: String = "Create library from jar..."

  override def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit = {
    def selectLibName(stableExpr: ScStableCodeReferenceElement, table: LibraryTable): String = {
      val refName = AmmoniteUtil.extractLibInfo(stableExpr).map(AmmoniteUtil.convertLibName).getOrElse(stableExpr.refName)
      var name = refName
      var count = 2
      
      while (table.getLibraryByName(name) != null) { //weird, but possible
        name = s"$refName ($count)"
        count += 1
      }
        
      name
    }
    
    
    myPsi match {
      case stableExpr: ScStableCodeReferenceElement =>
        AmmoniteUtil.findJarRoot(stableExpr).foreach {
          jarRoot =>
            extensions.inWriteAction {
              val tableModel = ProjectLibraryTable.getInstance(project).getModifiableModel
              val lib = tableModel.createLibrary(selectLibName(stableExpr, ProjectLibraryTable.getInstance(project)))

              val model = lib.getModifiableModel
              model.addRoot(jarRoot, OrderRootType.CLASSES)

              model.commit()
              tableModel.commit()

              Option(file.getVirtualFile).flatMap(f => ScalaUtil.getModuleForFile(f, project)).foreach {
                module =>
                  val moduleModel = ModuleRootManager.getInstance(module).getModifiableModel
                  moduleModel.addLibraryEntry(lib)
                  moduleModel.commit()

                  Option(ModuleStructureConfigurable.getInstance(project).getContext).foreach (
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

  override def getFamilyName: String = "Scala"
}
