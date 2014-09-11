package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessorBase, ChangeSignatureViewDescriptor}
import com.intellij.usageView.{UsageInfo, UsageViewDescriptor}
import com.intellij.util.FileContentUtil
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

/**
 * Nikolay.Tropin
 * 2014-09-01
 */
class ScalaChangeSignatureProcessor(project: Project, changeInfo: ScalaChangeInfo)
        extends ChangeSignatureProcessorBase(project, changeInfo) {

  override def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor =
    new ChangeSignatureViewDescriptor(changeInfo.getMethod)

  override def performRefactoring(usages: Array[UsageInfo]): Unit = {
      changeInfo.newParams.flatten.zipWithIndex.foreach {
        case (p, idx) =>
        p.defaultForJava = {
          if (changeInfo.isAddDefaultArgs) s"${changeInfo.getNewName}$$default$$${idx + 1}()"
          else p.defaultValue
        }
      }

    val sortedUsages = usages.sortBy {
      case _: ParameterUsageInfo => 0
      case _: MethodUsageInfo => 1
      case _: MethodValueUsageInfo => 1
      case _: ScalaNamedElementUsageInfo => 2
      case _ => 3
    }

    super.performRefactoring(sortedUsages)
  }

  override def performPsiSpoilingRefactoring(): Unit = {
    super.performPsiSpoilingRefactoring()
    if (!ApplicationManager.getApplication.isUnitTestMode) {
      FileContentUtil.reparseOpenedFiles()
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
  }
}
