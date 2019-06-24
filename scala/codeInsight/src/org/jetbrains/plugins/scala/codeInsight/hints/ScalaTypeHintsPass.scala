package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.psi.PsiElement
import org.atteo.evo.inflector.English
import org.jetbrains.plugins.scala.annotator.TypeDiff
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Group, Match}
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text, foldedAttributes, foldedString}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaTypeHintsPass._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.settings.annotations.Definition
import org.jetbrains.plugins.scala.settings.annotations.Definition.{FunctionDefinition, ValueDefinition, VariableDefinition}

private[codeInsight] trait ScalaTypeHintsPass {
  private val settings = ScalaCodeInsightSettings.getInstance
  import settings._

  protected def collectTypeHints(editor: Editor, root: PsiElement): Seq[Hint] = {
    if (editor.isOneLineMode || !(showFunctionReturnType || showPropertyType || showLocalVariableType)) Seq.empty
    else {
      for {
        element <- root.elements
        val definition = Definition(element)
        (tpe, body) <- typeAndBodyOf(definition)
        if showObviousType || !(definition.hasStableType || isTypeObvious(definition.name, tpe, body))
        info <- inlayInfoFor(definition, tpe)(editor.getColorsScheme)
      } yield info
    }.toSeq
  }

  private def typeAndBodyOf(definition: Definition): Option[(ScType, ScExpression)] = for {
    body <- definition.bodyCandidate
    tpe <- definition match {
      case ValueDefinition(value) => typeOf(value)
      case VariableDefinition(variable) => typeOf(variable)
      case FunctionDefinition(function) => typeOf(function)
      case _ => None
    }
  } yield (tpe, body)

  private def typeOf(member: ScValueOrVariable): Option[ScType] = {
    val flag = if (member.isLocal) showLocalVariableType else showPropertyType
    if (flag) member.`type`().toOption else None
  }

  private def typeOf(member: ScFunction): Option[ScType] =
    if (showFunctionReturnType) member.returnType.toOption else None


  private def inlayInfoFor(definition: Definition, returnType: ScType)(implicit scheme: EditorColorsScheme): Option[Hint] = for {
    anchor <- definition.parameterList
    suffix = definition match {
      case FunctionDefinition(function) if !function.hasAssign && function.hasUnitResultType => Seq(Text(" ="))
      case _ => Seq.empty
    }
    text = Text(": ") +: (partsOf(returnType) ++ suffix)
  } yield Hint(text, anchor, suffix = true, menu = Some("TypeHintsMenu"), relatesToPrecedingElement = true)

  private def partsOf(tpe: ScType)(implicit scheme: EditorColorsScheme): Seq[Text] = {
    def toText(diff: TypeDiff): Text = diff match {
      case Group(diffs @_*) =>
        Text(foldedString,
          foldedAttributes(error = false),
          expansion = Some(() => diffs.map(toText)))
      case Match(text, tpe) =>
        Text(text,
          tooltip = tpe.map(_.canonicalText.replaceFirst("_root_.", "")),
          navigatable = tpe.flatMap(_.extractClass))
    }
    TypeDiff.parse(tpe)
      .flattenTo(maxChars = presentationLength, groupLength = foldedString.length)
      .map(toText)
  }
}

private object ScalaTypeHintsPass {
  private val NonIdentifierChars = Set('\n', '(', '[', '{', ';', ',')

  def isTypeObvious(name: Option[String], tpe: ScType, body: ScExpression): Boolean =
    isTypeObvious(name.getOrElse(""), tpe.presentableText, body.getText.takeWhile(!NonIdentifierChars(_)))

  // SCL-14339
  // Text-based algorithm is easy to implement, easy to test, and is highly portable (e.g. can be reused in Kotlin)
  def isTypeObvious(name: String, tpe: String, body: String): Boolean =
    isTypeObvious(name, tpe) || isTypeObvious(body, tpe)

  private def isTypeObvious(name: String, tpe: String): Boolean =
    name.trim.nonEmpty && Predicate(name, tpe)

  private type Name = String
  private type Type = String
  private type Predicate = (Name, Type) => Boolean
  private type Combinator = (Name, Type) => Predicate => Boolean

  private val nameFirstLetterCase: Combinator = (name, tpe) => delegate =>
    delegate(fromLowerCase(name), tpe)

  private val typeFirstLetterCase: Combinator = (name, tpe) => delegate =>
    delegate(name, fromLowerCase(tpe))

  private def fromLowerCase(s: String): String =
    if (s.isEmpty) "" else s.substring(0, 1).toLowerCase + s.substring(1)

  // TODO use English.plural? But then both the name and the type would be plural,
  //   how to handle "coding conventions" and "known things"?
  private val Singular = "(.+?)(?:(?<=s|sh|ch|x|z)es|s)".r
  private val SequenceTypeArgument = "(?:Traversable|Iterable|Seq|IndexedSeq|LinearSeq|List|Vector|Array|Set)\\[(.+)\\]".r

  private val plural: Combinator = (name, tpe) => delegate => (name, tpe) match {
    case (Singular(noun), SequenceTypeArgument(argument)) if delegate(noun, argument) => true
    case _ => delegate(name, tpe)
  }

  private val PrepositionPrefix = "(.+)(?:In|Of|From|At|On|For|To|With|Before|After|Inside)".r

  private val prepositionSuffix: Combinator = (name, tpe) => delegate => name match {
    case PrepositionPrefix(namePrefix) if delegate(namePrefix, tpe) => true
    case _ => delegate(name, tpe)
  }

  private val GetSuffix = "get(\\p{Lu}.*)".r

  private val getPrefix: Combinator = (name, tpe) => delegate => name match {
    case GetSuffix(nameSuffix) if delegate(nameSuffix, tpe) => true
    case _ => delegate(name, tpe)
  }

  private val BooleanSuffix = "(?:is|has|have)(\\p{Lu}.*|)".r

  private val booleanPrefix: Combinator = (name, tpe) => delegate => name match {
    case BooleanSuffix(_) if tpe == "Boolean" => true
    case _ => delegate(name, tpe)
  }

  private val MaybeSuffix = "(?:maybe|optionOf)(\\p{Lu}.*)".r
  private val OptionArgument = "(?:Option|Some)\\[(.+)\\]".r

  private val optionPrefix: Combinator = (name, tpe) => delegate => (name, tpe) match {
    case (MaybeSuffix(nameSuffix), OptionArgument(typeArgument)) if delegate(nameSuffix, typeArgument) => true
    case _ => delegate(name, tpe)
  }

  private val codingConvention: Combinator = (name, tpe) => delegate => (name, tpe) match {
    case ("i" | "j" | "k" | "n", "Int" | "Integer") |
         ("b" | "bool" | "flag", "Boolean") |
         ("o" | "obj", "Object") |
         ("c" | "char", "Char" | "Character") |
         ("s" | "str", "String") => true
    case _ => delegate(name, tpe)
  }

  private val knownThing: Combinator = (name, tpe) => delegate => (name, tpe) match {
    case ("width" | "height" | "length" | "count" | "offset" | "index" | "start" | "begin" | "end", "Int" | "Integer") |
         ("name" | "message" | "text" | "description" | "prefix" | "suffix", "String") => true
    case _ => delegate(name, tpe)
  }

  private val NumberPrefix = "(.+?)\\d+".r

  private val numberSuffix: Combinator = (name, tpe) => delegate => name match {
    case NumberPrefix(namePrefix) if delegate(namePrefix, tpe) => true
    case _ => delegate(name, tpe)
  }

  private val NameTailingWord = "(\\p{Ll}.*)(\\p{Lu}.*?)".r
  private val TypeModifyingPrefixes = Set("is", "has", "have", "maybe", "optionOf")

  private val nameTailing: Combinator = (name, tpe) => delegate => delegate(name, tpe) || (name match {
    case NameTailingWord(prefix, word) if !TypeModifyingPrefixes(prefix) => delegate(word, tpe)
    case _ => false
  })

  private val TypeTailingWord = "\\w+(\\p{Lu}\\w*?)".r
  private val PrepositionSuffixes = Set("In", "Of", "From", "At", "On", "For", "To", "With", "Before", "After", "Inside")

  private val typeTailing: Combinator = (name, tpe) => delegate => delegate(name, tpe) || (tpe match {
    case TypeTailingWord(word) if !PrepositionSuffixes(word) => delegate(name, word)
    case _ => false
  })

  private val Predicate: Predicate =
    booleanPrefix(_, _)(
      optionPrefix(_, _)(
        numberSuffix(_, _)(
          prepositionSuffix(_, _)(
            typeTailing(_, _)(
              getPrefix(_, _)(
                nameTailing(_, _)(
                  plural(_, _)(
                    nameFirstLetterCase(_, _)(
                      codingConvention(_, _)(
                        knownThing(_, _)(
                          typeFirstLetterCase(_, _)(_ == _))))))))))))
}
