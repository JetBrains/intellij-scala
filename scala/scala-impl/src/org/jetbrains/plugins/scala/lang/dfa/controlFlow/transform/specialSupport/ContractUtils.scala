package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.specialSupport

import com.intellij.codeInspection.dataFlow.{HardcodedContracts, JavaMethodContractUtil, MethodContract}
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfo

import scala.jdk.CollectionConverters.CollectionHasAsScala

object ContractUtils {
  /**
   * Resolves method contracts for the invoked element.
   * Checks hardcoded contracts (e.g. Objects.requireNonNull) or explicit @Contract annotations.
   */
  //noinspection UnstableApiUsage
  def extractContracts(psiMethod: PsiMethod): Seq[MethodContract] = {
    val hardcoded = HardcodedContracts.getHardcodedContracts(psiMethod, null).asScala.toSeq
    lazy val contractAnnotation = JavaMethodContractUtil.findContractAnnotation(psiMethod)
    if (hardcoded.nonEmpty) hardcoded
    else if (contractAnnotation != null) {
      JavaMethodContractUtil.parseContracts(psiMethod, contractAnnotation).asScala.toSeq
    }
    else Seq.empty
  }

  def extractContracts(invocationInfo: InvocationInfo): Seq[MethodContract] =
    invocationInfo.invokedElement.map(_.psiElement)
      .filterByType[PsiMethod]
      .map(extractContracts)
      .getOrElse(Seq.empty)
}
