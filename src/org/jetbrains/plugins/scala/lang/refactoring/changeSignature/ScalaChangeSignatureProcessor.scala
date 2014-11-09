package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessorBase, ChangeSignatureViewDescriptor}
import com.intellij.usageView.{UsageInfo, UsageViewDescriptor}
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
        p.defaultForJava = changeInfo.defaultParameterForJava(p, idx)
    }

    val sortedUsages = usages.sortBy {
      case _: ParameterUsageInfo => 0
      case _: MethodUsageInfo => 1
      case _: AnonFunUsageInfo => 1
      case _: ScalaNamedElementUsageInfo => 2
      case _ => 3
    }

    super.performRefactoring(sortedUsages)
  }
}
