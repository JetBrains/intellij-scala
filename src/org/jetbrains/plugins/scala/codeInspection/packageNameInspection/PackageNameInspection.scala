package org.jetbrains.plugins.scala.codeInspection.packageNameInspection


import collection.mutable.ArrayBuffer
import com.intellij.codeInspection._

import com.intellij.psi.{JavaPsiFacade, PsiPackage, JavaDirectoryService, PsiFile}
import java.lang.String
import scala.lang.psi.api.ScalaFile
import scala.lang.psi.api.toplevel.packaging.ScPackageStatement
import scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class PackageNameInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Package Name Inspection"

  def getShortName: String = "Package Name"

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for files with package statement which not correspond to package structure"

  override def getID: String = "Package Name"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    file match {
      case file: ScalaFile => {
        if (file.getClasses.length == 0) return null
        val dir = file.getContainingDirectory
        if (dir == null) return null
        val pack = JavaDirectoryService.getInstance().getPackage(dir)
        if (pack == null) return null
        file.packageStatement match {
          case None => return null //todo: check it too
          case Some(packSt: ScPackageStatement) => {
            val buffer = new ArrayBuffer[LocalQuickFix]
            val ref = packSt.reference
            val p = ref.resolve
            p match {
              case p: PsiPackage => {
                if (p.getQualifiedName != pack.getQualifiedName) {
                  buffer += new ScalaRenamePackageQuickFix(file, pack.getQualifiedName)
                  buffer += new ScalaMoveToPackageQuickFix(file, p)
                }
              }
              case o: ScObject if o.isPackageObject => {
                if (o.getQualifiedName != pack.getQualifiedName) {
                  buffer += new ScalaRenamePackageQuickFix(file, pack.getQualifiedName)
                  val p = JavaPsiFacade.getInstance(file.getProject).findPackage(o.getQualifiedName)
                  if (p != null) {
                    buffer += new ScalaMoveToPackageQuickFix(file, p)                    
                  }
                }
              }
              case _ => {
                buffer += new ScalaRenamePackageQuickFix(file, pack.getQualifiedName)
              }
            }
            if (!buffer.isEmpty) {
              return Array[ProblemDescriptor](manager.createProblemDescriptor(packSt.reference, "Package name doesn't correspond to directories structure",
                buffer.toArray, ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
            } else {
              return null
            }
          }
        }
      }
      case _ => return null
    }
  }
}