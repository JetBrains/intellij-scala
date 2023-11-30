package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.hints.settings.{InlayProviderSettingsModel, InlaySettingsConfigurable, InlaySettingsConfigurableKt}
import com.intellij.ide.DataManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.Tree.{Leaf, Node}
import org.jetbrains.plugins.scala.annotator.TypeDiff.Match
import org.jetbrains.plugins.scala.annotator.hints.{Text, foldedAttributes, foldedString}
import org.jetbrains.plugins.scala.annotator.{Tree, TypeDiff}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocQuickInfoGenerator
import org.jetbrains.plugins.scala.extensions.{NullSafe, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.reflect.ClassTag

package object hints {
  private[hints] object ReferenceName {

    def unapply(expression: ScExpression): Option[(String, Seq[ScExpression])] = expression match {
      case MethodRepr(_, maybeExpression, maybeReference, arguments) =>
        maybeReference.orElse {
          maybeExpression.collect {
            case reference: ScReferenceExpression => reference
          }
        }.map(_.refName -> arguments)
      case _ => None
    }
  }

  private[hints] implicit class CamelCaseExt(private val string: String) extends AnyVal {

    def mismatchesCamelCase(that: String): Boolean =
      camelCaseIterator.zip(that.camelCaseIterator).exists {
        case (leftSegment, rightSegment) => leftSegment != rightSegment
      }

    def camelCaseIterator: Iterator[String] = for {
      name <- ScalaNamesUtil.isBacktickedName(string).iterator
      segment <- name.split("(?<!^)(?=[A-Z])").reverseIterator
    } yield segment.toLowerCase
  }

  private[hints] def textPartsOf(tpe: ScType, maxChars: Int, originalElement: PsiElement)(implicit scheme: EditorColorsScheme, context: TypePresentationContext): Seq[Text] = {
    def toText(diff: Tree[TypeDiff]): Text = diff match {
      case Node(diffs @_*) =>
        Text(foldedString,
          foldedAttributes(error = false),
          expansion = Some(() => diffs.map(toText)))
      case Leaf(Match(text, tpe)) =>
        def quickNavigateInfo = tpe.flatMap {
          case dt: ScDesignatorType => Option(ScalaDocQuickInfoGenerator.getQuickNavigateInfo(dt.element, originalElement))
          case _ => None
        }
        Text(text,
          tooltip = () => quickNavigateInfo.orElse(tpe.map(_.canonicalText.replaceFirst("_root_.", ""))),
          navigatable = tpe.flatMap(_.extractClass))
      case _ =>
        ???
    }
    TypeDiff.parse(tpe)
      .flattenTo(TypeDiff.lengthOf(nodeLength = foldedString.length), maxChars)
      .map(toText)
  }


  private val NonIdentifierChars = Set('\n', '(', '[', '{', ';', ',')

  def isTypeObvious(name: Option[String], tpe: ScType, body: ScExpression): Boolean =
    isTypeObvious(
      name.getOrElse(""),
      tpe.presentableText(TypePresentationContext.emptyContext),
      body.getText.takeWhile(!NonIdentifierChars(_))
    )

  // SCL-14339
  // Text-based algorithm is easy to implement, easy to test, and is highly portable (e.g. can be reused in Kotlin)
  def isTypeObvious(name: String, tpe: String, body: String): Boolean =
    isTypeObvious(name, tpe) || isTypeObvious(body, tpe)

  private def isTypeObvious(name: String, tpe: String): Boolean =
    name.trim.nonEmpty && Predicate(name, tpe)

  private type Name = String
  private type Type = String
  private type Predicate = (Name, Type) => Boolean
  private type Combinator = Predicate => Predicate

  private val equal: Predicate = _ == _

  private val nameFirstLetterCase: Combinator = delegate => (name, tpe) =>
    delegate(fromLowerCase(name), tpe)

  private val typeFirstLetterCase: Combinator = delegate => (name, tpe) =>
    delegate(name, fromLowerCase(tpe))

  private def fromLowerCase(s: String): String =
    if (s.isEmpty) "" else s.substring(0, 1).toLowerCase + s.substring(1)

  // TODO use English.plural? But then both the name and the type would be plural,
  //   how to handle "coding conventions" and "known things"?
  private val Singular = "(.+?)(?:(?<=s|sh|ch|x|z)es|s)".r
  private val SequenceTypeArgument = "(?:Traversable|Iterable|Seq|IndexedSeq|LinearSeq|List|Vector|Array|Set)\\[(.+)\\]".r

  private val plural: Combinator = delegate => (name, tpe) => (name, tpe) match {
    case (Singular(noun), SequenceTypeArgument(argument)) if delegate(noun, argument) => true
    case _ => delegate(name, tpe)
  }

  private val PrepositionPrefix = "(.+)(?:In|Of|From|At|On|For|To|With|Before|After|Inside)".r

  private val prepositionSuffix: Combinator = delegate => (name, tpe) => name match {
    case PrepositionPrefix(namePrefix) if delegate(namePrefix, tpe) => true
    case _ => delegate(name, tpe)
  }

  private val GetSuffix = "get(\\p{Lu}.*)".r

  private val getPrefix: Combinator = delegate => (name, tpe) => name match {
    case GetSuffix(nameSuffix) if delegate(nameSuffix, tpe) => true
    case _ => delegate(name, tpe)
  }

  private val BooleanSuffix = "(?:is|has|have)(\\p{Lu}.*|)".r

  private val booleanPrefix: Combinator = delegate => (name, tpe) => name match {
    case BooleanSuffix(_) if tpe == "Boolean" => true
    case _ => delegate(name, tpe)
  }

  private val MaybeSuffix = "(?:maybe|optionOf)(\\p{Lu}.*)".r
  private val OptionArgument = "(?:Option|Some)\\[(.+)\\]".r

  private val optionPrefix: Combinator = delegate => (name, tpe) => (name, tpe) match {
    case (MaybeSuffix(nameSuffix), OptionArgument(typeArgument)) if delegate(nameSuffix, typeArgument) => true
    case _ => delegate(name, tpe)
  }

  private val codingConvention: Combinator = delegate => (name, tpe) => (name, tpe) match {
    case ("i" | "j" | "k" | "n", "Int" | "Integer") |
         ("b" | "bool" | "flag", "Boolean") |
         ("o" | "obj", "Object") |
         ("c" | "char", "Char" | "Character") |
         ("s" | "str", "String") => true
    case _ => delegate(name, tpe)
  }

  private val knownThing: Combinator = delegate => (name, tpe) => (name, tpe) match {
    case ("width" | "height" | "length" | "count" | "offset" | "index" | "start" | "begin" | "end", "Int" | "Integer") |
         ("name" | "message" | "text" | "description" | "prefix" | "suffix", "String") => true
    case _ => delegate(name, tpe)
  }

  private val NumberPrefix = "(.+?)\\d+".r

  private val numberSuffix: Combinator = delegate => (name, tpe) => name match {
    case NumberPrefix(namePrefix) if delegate(namePrefix, tpe) => true
    case _ => delegate(name, tpe)
  }

  private val NameTailingWord = "(\\p{Ll}.*)(\\p{Lu}.*?)".r
  private val TypeModifyingPrefixes = Set("is", "has", "have", "maybe", "optionOf")

  private val nameTailing: Combinator = delegate => (name, tpe) => delegate(name, tpe) || (name match {
    case NameTailingWord(prefix, word) if !TypeModifyingPrefixes(prefix) => delegate(word, tpe)
    case _ => false
  })

  private val TypeTailingWord = "\\w+(\\p{Lu}\\w*?)".r
  private val PrepositionSuffixes = Set("In", "Of", "From", "At", "On", "For", "To", "With", "Before", "After", "Inside")

  private val typeTailing: Combinator = delegate => (name, tpe) => delegate(name, tpe) || (tpe match {
    case TypeTailingWord(word) if !PrepositionSuffixes(word) => delegate(name, word)
    case _ => false
  })

  private val Predicate: Predicate =
    Seq(
      booleanPrefix,
      optionPrefix,
      numberSuffix,
      prepositionSuffix,
      typeTailing,
      getPrefix,
      nameTailing,
      plural,
      nameFirstLetterCase,
      codingConvention,
      knownThing,
      typeFirstLetterCase,
    ).foldRight(equal)(_(_))


  def navigateToInlaySettings[S <: InlayProviderSettingsModel: ClassTag](project: Project): Unit = {
    DataManager.getInstance().getDataContextFromFocusAsync
      .`then` { context =>
        // First try to navigate currently open settings to the type hint settings
        NullSafe(Settings.KEY.getData(context))
          .map { settings =>
            val configurable = settings.find("inlay.hints")
            // Should not throw, but if it does, let exception analyzer know
            val inlayConfigurable = configurable.asInstanceOf[InlaySettingsConfigurable]
            inlayConfigurable.selectModel(ScalaLanguage.INSTANCE, _.is[S])
            settings.select(configurable)
            true
          }
          .orNull
      }
      .onProcessed { res =>
        if (res == null) {
          // if that doesn't work, instead open new settings window
          InlaySettingsConfigurableKt.showInlaySettings(
            project,
            ScalaLanguage.INSTANCE,
            _.is[S]
          )
        }
      }
  }
}
