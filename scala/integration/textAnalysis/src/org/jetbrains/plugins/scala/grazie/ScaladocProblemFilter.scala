package org.jetbrains.plugins.scala.grazie

import com.intellij.grazie.text.{ProblemFilter, TextContent, TextProblem}
import com.intellij.grazie.utils.ProblemFilterUtil
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

/**
 * Inspired by from [[com.intellij.grazie.ide.language.java.JavadocProblemFilter]] and adapted to Scala
 */
final class ScaladocProblemFilter extends ProblemFilter:

  override def shouldIgnore(problem: TextProblem): Boolean =
    val textContent = problem.getText
    textContent.getDomain match
      case TextContent.TextDomain.DOCUMENTATION =>
        textContent.getCommonParent match
          case _: ScDocTag =>
            ProblemFilterUtil.isUndecoratedSingleSentenceIssue(problem) ||
              ProblemFilterUtil.isInitialCasingIssue(problem)
          case _ => false
      case _ => false
  end shouldIgnore

end ScaladocProblemFilter
