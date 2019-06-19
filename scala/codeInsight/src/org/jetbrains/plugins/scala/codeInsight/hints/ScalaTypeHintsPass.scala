package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.psi.PsiElement
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
  } yield Hint(text, anchor, suffix = true, menu = Some("TypeHintsMenu"))

  private def partsOf(tpe: ScType)(implicit scheme: EditorColorsScheme): Seq[Text] = {
    def toText(diff: TypeDiff): Text = diff match {
      case Group(diffs) =>
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
  private val NonIdentifierChar = Set('\n', '(', '[', '{', ';', ',')

  def isTypeObvious(name: Option[String], tpe: ScType, body: ScExpression): Boolean =
    isTypeObvious(name.getOrElse(""), tpe.presentableText, body.getText.takeWhile(!NonIdentifierChar(_)))

  // SCL-14339
  // Text-based algorithm is easy to implement, easy to test, and is highly portable (e.g. can be reused in Kotlin)
  def isTypeObvious(name: String, tpe: String, body: String): Boolean =
    isTypeObvious(name, tpe) || isTypeObvious(body, tpe)

  private def isTypeObvious(name: String, tpe: String): Boolean =
    name.trim.nonEmpty && Predicate(name, tpe)

  private type Name = String
  private type Type = String
  private type Predicate = (Name, Type) => Boolean
  private type Chain = (Name, Type) => Predicate => Boolean

  private val firstLetterCase: Chain = (name, tpe) => delegate =>
    delegate(fromLowerCase(name), fromLowerCase(tpe))

  private def fromLowerCase(s: String): String =
    if (s.isEmpty) "" else s.substring(0, 1).toLowerCase + s.substring(1)

  private val Singular = "(.+?)(?:es|s)".r
  private val SequenceTypeArgument = "(?:Traversable|Iterable|Seq|IndexedSeq|LinearSeq|List|Vector|Array)\\[(.+)\\]".r

  private val plural: Chain = (name, tpe) => delegate => (name, tpe) match {
    case (Singular(noun), SequenceTypeArgument(argument)) if delegate(noun, argument) => true
    case _ => delegate(name, tpe)
  }

  private val PrepositionPrefix = "(.+)(?:In|Of|From|At|On|For|To|With|Before|After|Inside)".r

  private val prepositionSuffix: Chain = (name, tpe) => delegate => name match {
    case PrepositionPrefix(namePrefix) => delegate(namePrefix, tpe) || delegate(name, tpe)
    case _ => delegate(name, tpe)
  }

  private val GetSuffix = "get(\\p{Lu}.*)".r

  private val getPrefix: Chain = (name, tpe) => delegate => name match {
    case GetSuffix(nameSuffix) => delegate(nameSuffix, tpe) || delegate(name, tpe)
    case _ => delegate(name, tpe)
  }

  private val BooleanSuffix = "(?:is|has|have)(\\p{Lu}.*|)".r

  private val booleanPrefix: Chain = (name, tpe) => delegate => name match {
    case BooleanSuffix(_) if tpe == "Boolean" => true
    case _ => delegate(name, tpe)
  }

  private val MaybeSuffix = "(?:maybe|optionOf)(\\p{Lu}.*)".r
  private val OptionArgument = "(?:Option|Some)\\[(.+)\\]".r

  private val optionPrefix: Chain = (name, tpe) => delegate => (name, tpe) match {
    case (MaybeSuffix(nameSuffix), OptionArgument(typeArgument)) => delegate(nameSuffix, typeArgument) || delegate(name, tpe)
    case _ => delegate(name, tpe)
  }

  private val codingConvention: Chain = (name, tpe) => delegate => (name, tpe) match {
    case ("i" | "j" | "k" | "n", "Int" | "Integer") |
         ("b" | "bool", "Boolean") |
         ("o" | "obj", "Object") |
         ("c" | "char", "Char" | "Character") |
         ("s" | "str", "String") => true
    case _ => delegate(name, tpe)
  }

  private val TailingCapitalizedWord = ".+(\\p{Lu}.+?)".r

  private val knownThing: Chain = (name, tpe) => delegate => {
    val word = name match {
      case TailingCapitalizedWord(word) => word.toLowerCase
      case _ => name
    }
    (word, tpe) match {
      case ("width" | "height" | "length" | "count" | "offset" | "index" | "start" | "begin" | "end", "Int" | "Integer") |
           ("name" | "message" | "text" | "description" | "prefix" | "suffix", "String") => true
      case _ => delegate(name, tpe)
    }
  }

  private val NumberPrefix = "(.+?)\\d+".r

  private val numberSuffix: Chain = (name, tpe) => delegate => name match {
    case NumberPrefix(namePrefix) => delegate(namePrefix, tpe) || delegate(name, tpe)
    case _ => delegate(name, tpe)
  }

  private val Predicate: Predicate =
    numberSuffix(_, _)(
      knownThing(_, _)(
        codingConvention(_, _)(
          optionPrefix(_, _)(
            booleanPrefix(_, _)(
              getPrefix(_, _)(
                prepositionSuffix(_, _)(
                  plural(_, _)(
                    firstLetterCase(_, _)(_ == _)))))))))
}
