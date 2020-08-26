package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiEnumConstant}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, TypePresentationContext}

sealed trait PatternGenerationStrategy {
  def canBeExhaustive: Boolean

  def patterns: collection.Seq[PatternComponents]
}

object PatternGenerationStrategy {

  import extensions._

  implicit class StrategyExt(private val strategy: PatternGenerationStrategy) extends AnyVal {

    def adjustTypes(components: collection.Seq[PatternComponents],
                    caseClauses: collection.Seq[ScCaseClause]): Unit =
      adjustTypesOnClauses(
        addImports = strategy.isInstanceOf[DirectInheritorsGenerationStrategy],
        caseClauses.zip(components)
      )

    def createClauses(prefix: Option[String] = None,
                      suffix: Option[String] = None)
                     (implicit project: Project): (collection.Seq[PatternComponents], String) = {
      val components = strategy.patterns

      val clausesText = components
        .map(_.canonicalClauseText)
        .mkString(
          prefix.getOrElse(ScalaKeyword.MATCH + " {\n"),
          "\n",
          suffix.getOrElse("\n}")
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
          case collection.Seq() => null
          case _ => new EnumGenerationStrategy(enumClass, enumClass.qualifiedName, membersNames)
        }
      case ScDesignatorType(enumClass@JavaEnum(enumConstants)) =>
        enumConstants match {
          case Seq() => null
          case _ =>
            new EnumGenerationStrategy(
              enumClass,
              valueType.presentableText(TypePresentationContext.emptyContext),
              enumConstants.map(_.getName)
            )
        }
      case ScCompoundType(collection.Seq(ExtractClass(DirectInheritors(inheritors)), _*), _, _) =>
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
      case _ =>
        null
    }

    Option(strategy)
  }

  private[this] object ScalaEnumeration {

    private[this] val EnumerationFQN = "scala.Enumeration"

    def unapply(enumClass: ScObject): Option[collection.Seq[ScValue]] =
      if (enumClass.supers.map(_.qualifiedName).contains(EnumerationFQN))
        Some(enumClass.members.filterByType[ScValue])
      else
        None
  }

  private[this] object JavaEnum {

    def unapply(enumClass: PsiClass): Option[Seq[PsiEnumConstant]] =
      if (enumClass.isEnum)
        Some(enumClass.getFields.toSeq.filterByType[PsiEnumConstant])
      else
        None
  }

  private final class DirectInheritorsGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    private val Inheritors(namedInheritors, isSealed, isExhaustive) = inheritors

    override def canBeExhaustive: Boolean = isSealed ||
      namedInheritors.length < DirectInheritorsGenerationStrategy.NonSealedInheritorsThreshold

    override def patterns: Seq[PatternComponents] = namedInheritors.map {
      case scalaObject: ScObject => new StablePatternComponents(scalaObject)
      case CaseClassPatternComponents(components) => components
      case psiClass => new TypedPatternComponents(psiClass)
    } ++ (if (isExhaustive) None else Some(WildcardPatternComponents))
  }

  private object DirectInheritorsGenerationStrategy {
    private val NonSealedInheritorsThreshold = 5
  }

  private final class EnumGenerationStrategy(enumClass: PsiClass, qualifiedName: String,
                                             membersNames: collection.Seq[String]) extends PatternGenerationStrategy {

    override def canBeExhaustive = true

    override def patterns: collection.Seq[StablePatternComponents] = membersNames.map { name =>
      new StablePatternComponents(enumClass, qualifiedName + "." + name)
    }
  }
}
