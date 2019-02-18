package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.format.{Injection, InterpolatedStringParser, StringPart, Text}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandStringInterpolation extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e: ScInterpolatedStringLiteral if e.reference.isDefined =>
      InterpolatedStringParser.parse(e).foreach { parts =>
        // TODO it's probably simpler to parse the string directly, the format parser is for a different use case
        val normalizedParts = addInitialTextDelimiter(addTextDelimiters(extractSpecifiersIn(parts)))

        val strings = normalizedParts.collect {
          case Text(s) => s
        }

        val arguments = normalizedParts.collect {
          case Injection(expr, _) => expr
        }

        e.replace(code"StringContext(${strings.map(quote).mkString(", ")}).${e.reference.get.getText}(${@@(arguments)})")
      }
  }

  private def extractSpecifiersIn(parts: Seq[StringPart]): Seq[StringPart] = parts match {
    case Seq(Injection(expression, Some(specifier)), Text(s), t @ _*) =>
      Injection(expression, None) +: Text(specifier.format + s) +: extractSpecifiersIn(t)

    case Seq(Injection(expression, Some(specifier)), t @ _*) =>
      Injection(expression, None) +: Text(specifier.format) +: extractSpecifiersIn(t)

    case Seq(part, t @ _*) =>
      part +: extractSpecifiersIn(t)

    case Seq() =>
      Seq.empty
  }

  private def addTextDelimiters(parts: Seq[StringPart]): Seq[StringPart] = parts match {
    case Seq(injection1: Injection, injection2: Injection, t @ _*) =>
      injection1 +: Text("") +: addTextDelimiters(injection2 +: t)

    case Seq(injection: Injection) =>
      Seq(injection, Text(""))

    case Seq(part, t @ _*) =>
      part +: addTextDelimiters(t)

    case Seq() =>
      Seq.empty
  }

  private def addInitialTextDelimiter(parts: Seq[StringPart]): Seq[StringPart] = parts match {
    case it @ Seq(_: Injection, _ @ _*) => Text("") +: it
    case Seq() => Seq(Text(""))
    case it => it
  }
}
