package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

sealed abstract class NumberLiteralQuickFix(private[this] val literal: ScLiteral) extends IntentionAction {

  protected def transformText(text: String): Option[String]

  override final def getFamilyName: String = ScalaBundle.message("numeric.literal.family")

  override final def isAvailable(project: Project,
                                 editor: Editor,
                                 file: PsiFile): Boolean =
    literal.isValid &&
      literal.getManager.isInProject(file)

  override final def invoke(project: Project,
                            editor: Editor,
                            file: PsiFile): Unit =
    if (literal.isValid) {
      for {
        newText <- transformText(literal.getText)
        replacement = ScalaPsiElementFactory.createExpressionFromText(newText)(literal.getManager)
      } literal.replace(replacement)
    }

  override final def startInWriteAction: Boolean = super.startInWriteAction
}

object NumberLiteralQuickFix {

  final class ConvertToLong(literal: ScLiteral) extends NumberLiteralQuickFix(literal) {

    override def getText: String = ScalaBundle.message("convert.to.long.fix")

    override protected def transformText(text: String) = Some {
      ConvertToLong.transformText(text)
    }
  }

  private[annotator] object ConvertToLong {

    val Marker = 'L'

    def isApplicableTo(literal: ScLiteral, expectedType: ScType): Boolean = {
      val types = api.Long(literal.getProject) ::
        ScalaPsiElementFactory.createTypeFromText(
          "_root_.scala.math.BigInt",
          literal.getContext,
          literal
        ).toList

      types.exists(_.weakConforms(expectedType))
    }

    def transformText(text: String): String = text + Marker
  }

  final class ConvertOctToHex(literal: ScLiteral) extends NumberLiteralQuickFix(literal) {

    override def getText: String = ScalaBundle.message("convert.to.hex.fix")

    override protected def transformText(text: String): Option[String] =
      IntegerKind(text) match {
        case Oct =>
          // TODO isLong smells
          val isLong = text.last.toUpper == ConvertToLong.Marker
          val hexText = Oct.to(Hex)(text, isLong)
          Some {
            if (isLong) ConvertToLong.transformText(hexText) else hexText
          }
        case _ => None
      }
  }
}