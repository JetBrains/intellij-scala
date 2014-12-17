package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Ye Xianjin
 */
class ConvertOctalToHexFix(literal: ScLiteral) extends IntentionAction {
  val getText: String = "convert Octal string to Hex string"

  def getFamilyName: String = "Change ScLiteral"

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = literal.isValid && literal.getManager.isInProject(file)

  // the input text should be legal octal string
  private def convertOctalToHex(text: String): String = {
    import scala.math.BigInt
    val endsWithL = text.endsWith("l") || text.endsWith("L")
    val textWithoutL = if (endsWithL) text.substring(0, text.length - 1) else text
    val hexString = "0x" + BigInt(textWithoutL, 8).toString(16)
    if (endsWithL) hexString + "L" else hexString
  }

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!literal.isValid) return
    val text = literal.getText
    if (!(text.length >= 2 && text(0) == '0' && text(1).toLower != 'x')) return
    val psi = ScalaPsiElementFactory.createExpressionFromText(convertOctalToHex(text), literal.getManager)
    literal.replace(psi)
  }
}
