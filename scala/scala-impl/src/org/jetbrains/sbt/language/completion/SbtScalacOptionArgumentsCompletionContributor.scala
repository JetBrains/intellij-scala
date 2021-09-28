package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.sbt.language.completion.SbtPsiElementPatterns.scalacOptionsStringLiteralPattern
import org.jetbrains.sbt.language.completion.SbtScalacOptionArgumentsCompletionContributor._
import org.jetbrains.sbt.language.utils.SbtDependencyUtils
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.{getScalacOptionsForLiteralValue, scalacOptionsByFlag}

import scala.jdk.CollectionConverters._

class SbtScalacOptionArgumentsCompletionContributor extends CompletionContributor {
  extend(CompletionType.BASIC, pattern, completionProvider)
}

object SbtScalacOptionArgumentsCompletionContributor {
  private val scalacOptionsWithNonEmptyChoicesPatternCondition =
    new PatternCondition[ScStringLiteral]("scalacOptionWithNonEmptyChoices") {
      override def accepts(str: ScStringLiteral, context: ProcessingContext): Boolean =
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
            .addAllElements(completionsFor(strLiteral, flag, args).asJava)
        case _ =>
      }
    }

    private def completionsFor(place: PsiElement, flag: String, chosenArgs: Set[String])
                              (implicit parameters: CompletionParameters,
                               context: ProcessingContext): Iterable[LookupElement] =
      scalacOptionsByFlag.getOrElse(flag, Iterable.empty)
        .flatMap { option =>
          val projectScalaVersions = SbtDependencyUtils.getAllScalaVersionsOrDefault(place, majorOnly = true)

          option.choices.toList.flatMap { case (choice, scalaVersions) =>
            val matchingVersions = projectScalaVersions.filter(version => scalaVersions.exists(_.getVersion == version))

            Option.when(matchingVersions.nonEmpty && !chosenArgs(choice)) {
              LookupElementBuilder.create(choice)
                .withTailText(matchingVersions.mkString(" (", ", ", ")"))
                .bold()
            }
          }
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
