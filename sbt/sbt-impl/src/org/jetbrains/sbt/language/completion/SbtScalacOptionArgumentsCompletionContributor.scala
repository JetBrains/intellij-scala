package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.sbt.language.completion.SbtPsiElementPatterns.scalacOptionsStringLiteralPattern
import org.jetbrains.sbt.language.completion.SbtScalacOptionArgumentsCompletionContributor._
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.{getScalacOptionsForLiteralValue, scalacOptionsByFlag}

import scala.jdk.CollectionConverters._

class SbtScalacOptionArgumentsCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, pattern, completionProvider)
}

object SbtScalacOptionArgumentsCompletionContributor {
  private val scalacOptionsWithNonEmptyChoicesPatternCondition =
    condition[ScStringLiteral]("scalacOptionWithNonEmptyChoices") { str =>
      getScalacOptionsForLiteralValue(str).exists(_.choices.nonEmpty)
    }

  private val pattern = psiElement()
    .withParent(scalacOptionsStringLiteralPattern
      .`with`(scalacOptionsWithNonEmptyChoicesPatternCondition))

  private val completionProvider = new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = {
      implicit val params: CompletionParameters = parameters
      implicit val ctx: ProcessingContext = context

      val strLiteral = getContextOfType(positionFromParameters, classOf[ScStringLiteral])

      strLiteral match {
        case ScalacOptionArgCompletion(flag, args, prefix) =>
          resultSet
            .withPrefixMatcher(prefix)
            .addAllElements(completionsFor(flag, args).asJava)
        case _ =>
      }
    }

    private def completionsFor(flag: String, chosenArgs: Set[String])
                              (implicit parameters: CompletionParameters,
                               context: ProcessingContext): Iterable[LookupElement] = {
      lazy val projectScalaVersions = SbtScalacOptionUtils
        .projectVersionsSorted(parameters.getEditor.getProject, reverse = true)

      for {
        option <- scalacOptionsByFlag.getOrElse(flag, Iterable.empty)
        matchingVersions = projectScalaVersions.filter(option.choices.keySet)
        choice <- matchingVersions.toSet.flatMap(option.choices) &~ chosenArgs
      } yield LookupElementBuilder.create(choice)
        .withTailText(matchingVersions.map(_.getVersion).mkString(" (", ", ", ")"))
        .bold()
    }
  }
}

private object ScalacOptionArgCompletion {
  val flagAndArgsSeparator = ":"
  val argsSeparator = ","

  /**
   * @return
   *  - scalac option flag
   *  - selected scalac options arguments
   *  - prefix for completion based on completion parameters offset<br><br>
   *
   *    Example for <code>-language:higherKinds,dyn<caret>,postfixOps</code>:<br>
   *    flag = <code>"-language"</code><br>
   *    selected args = <code>Set("higherKinds", "dyn", "postfixOps")</code><br>
   *    prefix = <code>"dyn"</code>
   */
  def unapply(str: ScStringLiteral)(implicit parameters: CompletionParameters): Option[(String, Set[String], String)] = {
    val cleanStr = str.getValue
      .replace(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED, "")

    cleanStr.split(flagAndArgsSeparator, 2) match {
      case Array(flag, argsStr) =>
        val argsOffset = str.getTextOffset + flag.length + flagAndArgsSeparator.length
        Option.when(argsOffset < parameters.getOffset) {
          val argsSeparatorIdx = argsStr.lastIndexOf(argsSeparator, parameters.getOffset - argsOffset)
          val prefix =
            if (argsSeparatorIdx > 0) argsStr.substring(argsSeparatorIdx + argsSeparator.length)
            else argsStr
          val args = argsStr.split(argsSeparator).toSet.filter(_.nonEmpty)

          (flag, args, prefix)
        }
      case _ => None
    }
  }
}
