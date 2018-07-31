package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.lang.{Boolean => JBoolean}

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.{ElementProcessingHintPass, ModificationStampHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.types.api.JavaArrayType
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.ScTypePresentationExt
import org.jetbrains.plugins.scala.settings.annotations.Definition

class ScalaTypeHintsPass(rootElement: ScalaFile,
                         editor: Editor,
                         stampHolder: ModificationStampHolder)
  extends ElementProcessingHintPass(rootElement, editor, stampHolder) {

  import ScalaTypeHintsPass._

  override def isAvailable(virtualFile: VirtualFile): Boolean = {
    val settings = ScalaCodeInsightSettings.getInstance
    settings.showFunctionReturnType || settings.showPropertyType || settings.showLocalVariableType
  }

  override def collectElementHints(element: PsiElement, collector: kotlin.jvm.functions.Function2[_ >: Integer, _ >: String, kotlin.Unit]): Unit = {
    implicit val settings: ScalaCodeInsightSettings = ScalaCodeInsightSettings.getInstance

    if (!settings.showObviousType && Definition(element).isTypeStable) return

    Some(element).collect {
      case function@Parameters(parameters) if settings.showFunctionReturnType =>
        (parameters, function.returnType)
      case member@PatternList(list)
        //noinspection ScalaUnnecessaryParentheses
        if (if (member.isLocal) settings.showLocalVariableType else settings.showPropertyType) =>
        (list, member.`type`())
    }.collect {
      case (anchor, Right(CodeText(text))) =>
        InlayInfo(text, ScalaTokenTypes.tCOLON, anchor, relatesToPrecedingText = true)
    }.foreach { info =>
      collector.invoke(info.getOffset, info.getText)
    }
  }

  override def getHintKey: Key[JBoolean] = ScalaTypeInlayKey

  override def createRenderer(text: String): HintRenderer = new HintRenderer(text) {
    override def getContextMenuGroupId: String = "TypeHintsMenu"
  }
}

object ScalaTypeHintsPass {

  private val ScalaTypeInlayKey = Key.create[JBoolean]("SCALA_TYPE_INLAY_KEY")

  private object Parameters {

    def unapply(definition: ScFunctionDefinition): Option[ScParameters] =
      if (definition.hasExplicitType || definition.isConstructor) None
      else Some(definition.parameterList)
  }

  private object PatternList {

    def unapply(definition: ScValueOrVariable): Option[ScPatternList] =
      definition match {
        case _ if definition.hasExplicitType => None
        case value: ScPatternDefinition => Some(value.pList)
        case variable: ScVariableDefinition => Some(variable.pList)
        case _ => None
      }
  }

  private object CodeText {

    def unapply(`type`: ScType)
               (implicit settings: ScalaCodeInsightSettings): Option[String] =
      `type` match {
        case CodeText(text) if limited(text) => Some(text)
        case FoldedCodeText(text) if limited(text) => Some(text)
        case _ => None
      }

    private def limited(text: String)
                       (implicit settings: ScalaCodeInsightSettings): Boolean =
      text.length <= settings.presentationLength

    private object CodeText {

      def unapply(`type`: ScType) = Some(`type`.codeText)
    }

    private object FoldedCodeText {

      private[this] val Ellipsis = "..."

      def unapply(`type`: ScType): Option[String] = `type` match {
        case ScCompoundType(components, signatures, types) =>
          val suffix = if (signatures.nonEmpty || types.nonEmpty) s" {$Ellipsis}" else ""
          val text = components match {
            case Seq(CodeText(head), _, _*) => s"$head with $Ellipsis"
            case Seq(CodeText(head)) => head + suffix
            case Seq() => "AnyRef" + suffix
          }
          Some(text)
        case ScParameterizedType(CodeText(text), typeArguments) =>
          val suffix = Seq.fill(typeArguments.size)(Ellipsis)
            .commaSeparated(model = Model.SquareBrackets)
          Some(text + suffix)
        case JavaArrayType(_) => Some(s"Array[$Ellipsis]")
        case _ => None
      }
    }

  }

}
