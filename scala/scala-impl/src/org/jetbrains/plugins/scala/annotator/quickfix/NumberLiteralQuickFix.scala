package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{Bin, Hex, Oct}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral.Numeric
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScIntegerLiteral, ScLongLiteral}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

sealed abstract class NumberLiteralQuickFix[L <: Numeric](private[this] val literal: L) extends IntentionAction {

  protected def transformText(text: String): String

  override final def getFamilyName: String = ScalaBundle.message("numeric.literal.family")

  override final def isAvailable(project: Project,
                                 editor: Editor,
                                 file: PsiFile): Boolean =
    literal.isValid

  override final def invoke(project: Project,
                            editor: Editor,
                            file: PsiFile): Unit =
    if (literal.isValid) replaceLiteral(literal)

  override final def startInWriteAction: Boolean = true

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    replaceLiteral(PsiTreeUtil.findSameElementInCopy(literal, file))
    IntentionPreviewInfo.DIFF
  }

  private def replaceLiteral(literal: L): Unit = {
    val newText = transformText(literal.getText)
    literal.replace {
      ScalaPsiElementFactory.createExpressionFromText(newText, literal)(literal)
    }
  }
}

object NumberLiteralQuickFix {

  final class ConvertToLong(literal: ScIntegerLiteral) extends NumberLiteralQuickFix(literal) {

    override def getText: String = ScalaBundle.message("convert.to.long.fix")

    override protected def transformText(text: String): String =
      ConvertToLong.transformText(text)
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

  final class ConvertOctToHex(literal: Numeric,
                              isLong: Boolean) extends NumberLiteralQuickFix(literal) {

    override def getText: String = ScalaBundle.message("convert.octal.to.hex.fix")

    override protected def transformText(text: String): String =
      Oct.to(Hex)(text, isLong) match {
        case result if isLong => ConvertToLong.transformText(result)
        case result => result
      }
  }

  final class ConvertBinaryToHex(literal: Numeric,
                                 isLong: Boolean) extends NumberLiteralQuickFix(literal) {

    override def getText: String = ScalaBundle.message("convert.binary.to.hex.fix")

    override protected def transformText(text: String): String =
      Bin.to(Hex)(text, isLong) match {
        case result if isLong => ConvertToLong.transformText(result)
        case result => result
      }
  }

  final class ConvertMarker(literal: ScLongLiteral) extends NumberLiteralQuickFix(literal) {

    override def getText: String = ScalaBundle.message("lowercase.long.marker.fix")

    override protected def transformText(text: String): String =
      ConvertToLong.transformText(text.dropRight(1))
  }

  object ConvertMarker {

    def isApplicableTo(literal: ScLongLiteral): Boolean =
      literal.getText.last.isLower
  }
}
