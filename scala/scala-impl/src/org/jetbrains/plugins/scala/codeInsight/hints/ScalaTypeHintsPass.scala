package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.lang.{Boolean => JBoolean}

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints
import com.intellij.codeInsight.hints.{ElementProcessingHintPass, ModificationStampHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.StringsExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.JavaArrayType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
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

    if (settings.showForObviousTypes || Definition(element).isTypeStable) return

    val maybeInfo = element match {
      case f@TypelessFunction(anchor) if settings.showFunctionReturnType =>
        f.returnType.toInlayInfo(anchor)
      case v@TypelessValueOrVariable(anchor)
        //noinspection ScalaUnnecessaryParentheses
        if (if (v.isLocal) settings.showLocalVariableType else settings.showPropertyType) =>
        v.`type`().toInlayInfo(anchor)
      case _ => None
    }

    maybeInfo.foreach { info =>
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

  private object TypelessFunction {

    def unapply(definition: ScFunctionDefinition): Option[ScalaPsiElement] =
      if (definition.hasExplicitType || definition.isConstructor) None
      else Some(definition.parameterList)
  }

  private object TypelessValueOrVariable {

    def unapply(definition: ScValueOrVariable): Option[ScalaPsiElement] =
      if (definition.hasExplicitType) None
      else definition match {
        case value: ScPatternDefinition => Some(value.pList)
        case variable: ScVariableDefinition => Some(variable.pList)
        case _ => None
      }
  }

  private implicit class TypeResultExt(private val result: TypeResult) {

    def toInlayInfo(anchor: ScalaPsiElement)
                   (implicit settings: ScalaCodeInsightSettings): Option[hints.InlayInfo] =
      result.toOption.collect {
        case PresentableText(Limited(text)) => text
        case FoldedPresentableText(Limited(text)) => text
      }.map(InlayInfo(_, ScalaTokenTypes.tCOLON, anchor, relatesToPrecedingText = true))
  }

  private[this] object Limited {

    def unapply(text: String)
               (implicit settings: ScalaCodeInsightSettings): Option[String] =
      if (text.length <= settings.presentationLength) Some(text) else None
  }

  private[this] object PresentableText {

    def unapply(`type`: ScType): Some[String] =
      Some(`type`.codeText)
  }

  private[this] object FoldedPresentableText {

    private[this] val Ellipsis = "..."

    def unapply(`type`: ScType): Option[String] = `type` match {
      case ScCompoundType(comps, signs, types) =>
        val mainComponent = comps.headOption.map(_.codeText).getOrElse("AnyRef")
        val text =
          if (comps.size > 1) s"$mainComponent with $Ellipsis"
          else if (signs.size + types.size > 0) s"$mainComponent {$Ellipsis}"
          else mainComponent
        Some(text)
      case ScParameterizedType(designator, typeArguments) =>
        val arguments = Seq.fill(typeArguments.size)(Ellipsis)
        Some(s"${designator.codeText}[${arguments.commaSeparated()}]")
      case JavaArrayType(_) => Some(s"Array[$Ellipsis]")
      case _ => None
    }
  }

}
