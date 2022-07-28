package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessorBase, ChangeSignatureViewDescriptor}
import com.intellij.usageView.{UsageInfo, UsageViewDescriptor}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

final class ScalaChangeSignatureProcessor(changeInfo: ScalaChangeInfo)
                                         (implicit project: Project)
        extends ChangeSignatureProcessorBase(project, changeInfo) {

  override def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor =
    new ChangeSignatureViewDescriptor(changeInfo.getMethod)

  override def performRefactoring(usages: Array[UsageInfo]): Unit = {
    changeInfo.newParams.flatten.zipWithIndex.foreach {
      case (p, idx) =>
        p.defaultForJava = changeInfo.defaultParameterForJava(p, idx)
    }

    super.performRefactoring(usages)
  }
}
