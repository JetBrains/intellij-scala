package org.jetbrains.plugins.scala.lang.completion.clauses

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{JavaEnum, ObjectExt, ScalaEnumeration}
import org.jetbrains.plugins.scala.lang.completion.{ScalaKeyword, toValueType}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumSingletonCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, TypePresentationContext}

sealed trait PatternGenerationStrategy {
  def canBeExhaustive: Boolean

  def patterns: Seq[PatternComponents]
}

object PatternGenerationStrategy {

  implicit class StrategyExt(private val strategy: PatternGenerationStrategy) extends AnyVal {

    def adjustTypes(components: Seq[PatternComponents],
                    caseClauses: Seq[ScCaseClause]): Unit =
      adjustTypesOnClauses(
        addImports = strategy.is[DirectInheritorsGenerationStrategy],
        caseClauses.zip(components)
      )

    def createClauses(useIndentationBasedSyntax: Boolean = false,
                      prefix: Option[String] = None,
                      suffix: Option[String] = None,
                      rightHandSide: String = "")
                     (implicit project: Project): (Seq[PatternComponents], String) = {
      val components = strategy.patterns

      val clausesText = components
        .map(_.canonicalClauseText + rightHandSide)
        .mkString(
          prefix.getOrElse(ScalaKeyword.MATCH + (if (useIndentationBasedSyntax) "\n" else " {\n")),
          "\n",
          suffix.getOrElse(if (useIndentationBasedSyntax) "" else "\n}")
        )

      (components, clausesText)
    }
  }

  def unapply(`type`: ScType)
             (implicit parameters: ClauseCompletionParameters): Option[PatternGenerationStrategy] = {
    val valueType = toValueType(`type`)
    val strategy = valueType match {
      case ScProjectionType(DesignatorOwner(enumClass@ScalaEnumeration(values)), _) =>
        val membersNames = for {
          value <- values
          if isAccessible(value)
          if value.`type`().exists(_.conforms(valueType))

          declaredName <- value.declaredNames
        } yield declaredName

        membersNames match {
          case Seq() => null
          case _ => new EnumGenerationStrategy(enumClass, enumClass.qualifiedName, membersNames)
        }
      case DesignatorOwner(enumClass@JavaEnum(enumConstants)) =>
        enumConstants match {
          case Seq() => null
          case _ =>
            new EnumGenerationStrategy(
              enumClass,
              valueType.presentableText(TypePresentationContext.emptyContext),
              enumConstants.map(_.getName)
            )
        }
      case ScCompoundType(Seq(ExtractClass(DirectInheritors(inheritors)), _*), _, _) =>
        val Inheritors(namedInheritors, isSealed, isExhaustive) = inheritors

        val appropriateNamedInheritors = for {
          inheritor <- namedInheritors
          if ScDesignatorType(inheritor).conforms(valueType)
        } yield inheritor

        if (appropriateNamedInheritors.isEmpty) null
        else new DirectInheritorsGenerationStrategy(
          Inheritors(appropriateNamedInheritors, isSealed, isExhaustive)
        )
      case ExtractClass(DirectInheritors(inheritors)) =>
        new DirectInheritorsGenerationStrategy(inheritors)
      case ExtractClass(scEnum: ScEnum) =>
        val cases = scEnum.cases
        if (cases.nonEmpty) {
          val inheritors = Inheritors(cases.toList, isSealed = true, isExhaustive = true)
          new DirectInheritorsGenerationStrategy(inheritors)
        } else null
      case _ =>
        null
    }

    Option(strategy)
  }

  private final class DirectInheritorsGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    private val Inheritors(namedInheritors, isSealed, isExhaustive) = inheritors

    override def canBeExhaustive: Boolean = isSealed ||
      namedInheritors.length < DirectInheritorsGenerationStrategy.NonSealedInheritorsThreshold

    override def patterns: Seq[PatternComponents] = namedInheritors.map {
      case enumCase: ScEnumSingletonCase => new StablePatternComponents(enumCase)
      case scalaObject: ScObject => new StablePatternComponents(scalaObject)
      case CaseClassPatternComponents(components) => components
      case psiClass => new TypedPatternComponents(psiClass)
    } ++ (if (isExhaustive) None else Some(WildcardPatternComponents))
  }

  private object DirectInheritorsGenerationStrategy {
    private val NonSealedInheritorsThreshold = 5
  }

  private final class EnumGenerationStrategy(enumClass: PsiClass, qualifiedName: String,
                                             membersNames: Seq[String]) extends PatternGenerationStrategy {

    override def canBeExhaustive = true

    override def patterns: Seq[StablePatternComponents] = membersNames.map { name =>
      new StablePatternComponents(enumClass, qualifiedName + "." + name)
    }
  }
}
