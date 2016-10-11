package org.jetbrains.plugins.scala.codeInspection.bundled

import com.intellij.codeInspection.{LocalInspectionTool, LocalInspectionToolSession, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.codeInspection.bundled.BundledCompoundInspection.MyInspectionVisitorWrapper

/**
  * User: Dmitry.Naydanov
  * Date: 03.10.16.
  */
class BundledCompoundInspection extends LocalInspectionTool {
  override def getDisplayName: String = "Scala bundled inspections runner"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new MyInspectionVisitorWrapper(
      BundledInspectionStoreComponent.getInstance(holder.getProject).getFilteredInspections.map(_.actionFor(holder))
    )
  }

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, 
                            session: LocalInspectionToolSession): PsiElementVisitor = buildVisitor(holder, isOnTheFly)
}


object BundledCompoundInspection {
  class MyInspectionVisitorWrapper(allActions: Iterable[PartialFunction[PsiElement, Any]]) extends PsiElementVisitor {
    override def visitElement(element: PsiElement) {
      allActions.find (_.isDefinedAt(element)) map (_.apply(element))
    }
  }
}