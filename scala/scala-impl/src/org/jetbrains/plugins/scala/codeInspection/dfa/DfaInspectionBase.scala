package org.jetbrains.plugins.scala.codeInspection.dfa

import com.intellij.codeInspection.{LocalInspectionTool, LocalInspectionToolSession, ProblemsHolder}
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.dfa.DfaInspectionBase.visitorProviderKey
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaVisitor
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaDfaResult

abstract class DfaInspectionBase(createReporter: ProblemsHolder => ScalaDfaResult => Unit) extends LocalInspectionTool {
  override def isAvailableForFile(file: PsiFile): Boolean = super.isAvailableForFile(file) && !DumbService.isDumb(file.getProject)
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = ???
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor = {
    var provider = session.getUserData(visitorProviderKey)

    if (provider == null) {
      val file = session.getFile
      provider = new ScalaDfaVisitor.AsyncProvider(file.getProject, file)
      session.putUserData(visitorProviderKey, provider)
    }

    provider.visitor(createReporter(holder))
  }

  override def inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder): Unit = {
    val visitorProvider = session.getUserData(visitorProviderKey)
    visitorProvider.finish()
    super.inspectionFinished(session, problemsHolder)
  }
}

object DfaInspectionBase {
  private val visitorProviderKey = Key.create[ScalaDfaVisitor.AsyncProvider]("ScalaDfaVisitor.AsyncProvider")
}