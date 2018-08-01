package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.lang.{Boolean => JBoolean}

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.{ElementProcessingHintPass, InlayInfo, ModificationStampHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
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
    val definition = Definition(element)

    for {
      ReturnType(returnType) <- Some(definition)
      if settings.showObviousType || !isObviousFor(returnType, definition)

      info <- createInlayInfo(definition, returnType)
    } collector.invoke(info.getOffset, info.getText)
  }

  override def getHintKey: Key[JBoolean] = ScalaTypeInlayKey

  override def createRenderer(text: String): HintRenderer = new HintRenderer(text) {
    override def getContextMenuGroupId: String = "TypeHintsMenu"
  }
}

object ScalaTypeHintsPass {

  import Definition._

  private val ScalaTypeInlayKey = Key.create[JBoolean]("SCALA_TYPE_INLAY_KEY")

  private object ReturnType {

    def unapply(definition: Definition)
               (implicit settings: ScalaCodeInsightSettings): Option[ScType] =
      definition match {
        case ValueDefinition(value) => unapply(value)
        case VariableDefinition(variable) => unapply(variable)
        case FunctionDefinition(function) => unapply(function)
        case _ => None
      }

    private def unapply(member: ScValueOrVariable)
                       (implicit settings: ScalaCodeInsightSettings) = {
      val flag = if (member.isLocal) settings.showLocalVariableType else settings.showPropertyType
      if (flag) member.`type`().toOption else None
    }

    private def unapply(member: ScFunction)
                       (implicit settings: ScalaCodeInsightSettings) =
      if (settings.showFunctionReturnType) member.returnType.toOption
      else None
  }

  private def isObviousFor(returnType: ScType, definition: Definition): Boolean =
    definition.hasStableType ||
      definition.bodyCandidate
        .zip(returnType.extractClass)
        .exists {
          case (ReferenceName(name, _), clazz) =>
            !name.mismatchesCamelCase(clazz.name)
          case _ => false
        }

  private def createInlayInfo(definition: Definition, returnType: ScType)
                             (implicit settings: ScalaCodeInsightSettings) = {
    import ScalaTokenTypes._
    for {
      anchor <- definition.parameterList
      offset = anchor.getTextRange.getEndOffset

      CodeText(codeText) <- Some(returnType)
      infix = s"$tCOLON $codeText"

      text = definition match {
        case FunctionDefinition(function) if function.hasUnitResultType =>
          val prefix = if (function.isParameterless) s"$tLPARENTHESIS$tRPARENTHESIS" else ""
          val suffix = if (function.hasAssign) "" else " " + tASSIGN
          s"$prefix$infix$suffix"
        case _ => infix
      }
    } yield new InlayInfo(text, offset, false, true, true)
  }

  private[this] object CodeText {

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
