package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.psi.{PsiClass, PsiElement, PsiEnumConstant}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.isAccessible

sealed trait PatternGenerationStrategy {
  def patterns: Seq[PatternComponents]
}

object PatternGenerationStrategy {

  import extensions._

  implicit class StrategyExt(private val strategy: PatternGenerationStrategy) extends AnyVal {

    def adjustTypes(components: Seq[PatternComponents],
                    caseClauses: Seq[ScCaseClause]): Unit =
      adjustTypesOnClauses(
        addImports = strategy.isInstanceOf[DirectInheritorsGenerationStrategy],
        caseClauses.zip(components): _*
      )

    def createClauses(prefix: Option[String] = None,
                      suffix: Option[String] = None)
                     (implicit place: PsiElement): (Seq[PatternComponents], String) = {
      val components = strategy.patterns

      val clausesText = components.map(_.canonicalClauseText)
        .mkString(
          prefix.getOrElse(ScalaKeyword.MATCH + " {\n"),
          "\n",
          suffix.getOrElse("\n}")
        )

      (components, clausesText)
    }
  }

  def unapply(`type`: ScType)
             (implicit place: PsiElement): Option[PatternGenerationStrategy] = {
    val valueType = `type`.extractDesignatorSingleton.getOrElse(`type`)
    val strategy = valueType match {
      case ScProjectionType(DesignatorOwner(enumClass@ScalaEnumeration(values)), _) =>
        val membersNames = for {
          value <- values
          if isAccessible(value, place, forCompletion = true)
          if value.`type`().exists(_.conforms(valueType))

          declaredName <- value.declaredNames
        } yield declaredName

        membersNames match {
          case Seq() => null
          case _ => new EnumGenerationStrategy(enumClass, enumClass.qualifiedName, membersNames)
        }
      case ScDesignatorType(enumClass@JavaEnum(enumConstants)) =>
        enumConstants match {
          case Seq() => null
          case _ =>
            new EnumGenerationStrategy(
              enumClass,
              valueType.presentableText,
              enumConstants.map(_.getName)
            )
        }
      case ExtractClass(DirectInheritors(inheritors)) =>
        new DirectInheritorsGenerationStrategy(inheritors)
      case _ =>
        null
    }

    Option(strategy)
  }

  private[this] object ScalaEnumeration {

    private[this] val EnumerationFQN = "scala.Enumeration"

    def unapply(enumClass: ScObject): Option[Seq[ScValue]] =
      if (enumClass.supers.map(_.qualifiedName).contains(EnumerationFQN))
        Some(enumClass.members.filterBy[ScValue])
      else
        None
  }

  private[this] object JavaEnum {

    def unapply(enumClass: PsiClass): Option[Seq[PsiEnumConstant]] =
      if (enumClass.isEnum)
        Some(enumClass.getFields.toSeq.filterBy[PsiEnumConstant])
      else
        None
  }

  private final class DirectInheritorsGenerationStrategy(inheritors: Inheritors) extends PatternGenerationStrategy {

    override def patterns: Seq[PatternComponents] = {
      val Inheritors(namedInheritors, isExhaustive) = inheritors

      namedInheritors.map {
        case scalaObject: ScObject => new StablePatternComponents(scalaObject)
        case CaseClassPatternComponents(components) => components
        case psiClass => new TypedPatternComponents(psiClass)
      } ++ (if (isExhaustive) None else Some(WildcardPatternComponents))
    }
  }

  private final class EnumGenerationStrategy(enumClass: PsiClass, qualifiedName: String,
                                             membersNames: Seq[String]) extends PatternGenerationStrategy {

    override def patterns: Seq[StablePatternComponents] = membersNames.map { name =>
      new StablePatternComponents(enumClass, qualifiedName + "." + name)
    }
  }
}
