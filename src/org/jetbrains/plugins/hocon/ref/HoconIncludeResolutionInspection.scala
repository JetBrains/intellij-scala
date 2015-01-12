package org.jetbrains.plugins.hocon.ref

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import org.jetbrains.plugins.hocon.psi.{HString, HoconElementVisitor}

class HoconIncludeResolutionInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
    new HoconElementVisitor {
      override def visitHString(element: HString) =
        element.getFileReferences.foreach { ref =>
          if (!ref.isSoft && ref.multiResolve(false).isEmpty) {
            holder.registerProblem(ref, ProblemsHolder.unresolvedReferenceMessage(ref),
              ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          }
        }
    }
}
