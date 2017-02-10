package org.jetbrains.plugins.scala.conversion.copy.plainText

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.lang.Boolean
import java.lang.System.lineSeparator

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.conversion.copy.{Association, SingularCopyPastePostProcessor}
import org.jetbrains.plugins.scala.conversion.{ConverterUtil, JavaToScala}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil
import org.jetbrains.plugins.scala.{ScalaBundle, extensions}

/**
  * Created by Kate Ustyuzhanina on 12/19/16.
  */
class TextJavaCopyPastePostProcessor extends SingularCopyPastePostProcessor[TextBlockTransferableData] {
  override protected def collectTransferableData0(file: PsiFile, editor: Editor, startOffsets: Array[Int],
                                                  endOffsets: Array[Int]): TextBlockTransferableData = {
    null
  }

  override protected def extractTransferableData0(content: Transferable): TextBlockTransferableData = {
    if (!content.isDataFlavorSupported(ConverterUtil.ConvertedCode.Flavor) && content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      val text = content.getTransferData(DataFlavor.stringFlavor).asInstanceOf[String]
      new ConverterUtil.ConvertedCode(text, Array.empty[Association], false)
    } else {
      null
    }
  }

  override protected def processTransferableData0(project: Project, editor: Editor, bounds: RangeMarker,
                                                  caretOffset: Int, ref: Ref[Boolean], value: TextBlockTransferableData): Unit = {

    if (!ScalaProjectSettings.getInstance(project).isEnableJavaToScalaConversion) return
    if (!PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument).isInstanceOf[ScalaFile]) return

    val (text, _, _) = value match {
      case code: ConverterUtil.ConvertedCode => (code.data, code.associations, code.showDialog)
      case _ => ("", Array.empty[Association], true)
    }

    if (text == null || text == "" || project == null) {
      return
    }

    if (PlainTextCopyUtil.isValidScalaFile(text, project)) {
      return
    }

    // TODO: Collect available imports in current scope. Use them while converting
    computejavaContext(text, project).foreach { javaCodeWithContext =>
      val needShowDialog = !ScalaProjectSettings.getInstance(project).isDontShowConversionDialog

      if (!needShowDialog || ConverterUtil.shownDialog(ScalaBundle.message("scala.copy.from.text"), project).isOK) {
        UsageTrigger.trigger(ScalaBundle.message("scala.convert.from.java.text"))

        extensions.inWriteAction {
          val project = javaCodeWithContext.project

          createFileWithAdditionalImports(javaCodeWithContext).foreach { javaFile =>
            val convertedText = convert(javaFile, javaCodeWithContext.context, project)

            ConverterUtil.replaceByConvertedCode(editor, bounds, convertedText)

            editor.getCaretModel.moveToOffset(bounds.getStartOffset + convertedText.length)

            ConverterUtil.withSpecialStyleIn(project) {
              val manager = CodeStyleManager.getInstance(project)
              manager.reformatText(
                PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument),
                bounds.getStartOffset, bounds.getStartOffset + convertedText.length
              )
            }
          }
        }
      }
    }
  }

  def convert(javaFile: PsiJavaFile, context: CopyContext, project: Project): String = {
    def convertStatement(psiElement: PsiElement): String =
      Option(psiElement).map(holder => JavaToScala.convertPsisToText(Array(holder))).getOrElse("")

    def newLine(text: String): String = if (text == "") text else text + lineSeparator()

    val javaFileLen = javaFile.getText.length
    val (begin, end) = context match {
      case _: FileContext => (0, javaFileLen)
      case part =>
        (part.prefix.length + javaFile.getImportList.getTextRange.getEndOffset,
          javaFileLen - part.prefix.length)
    }

    val elementsToConvert =
      ConverterUtil.collectTopElements(
        begin,
        end,
        javaFile
      ).filterNot(el => el.isInstanceOf[PsiImportList] || el.isInstanceOf[PsiPackageStatement])

    val scalaFile = new ScalaCodeFragment(project, JavaToScala.convertPsisToText(elementsToConvert))

    val scalaFileText = scalaFile.getText
    ConverterUtil.runInspections(scalaFile, project, 0, scalaFileText.length)
    TypeAnnotationUtil.removeAllTypeAnnotationsIfNeeded(ConverterUtil.collectTopElements(0, scalaFileText.length, scalaFile))

    newLine(convertStatement(javaFile.getPackageStatement)) +
      newLine(convertStatement(javaFile.getImportList)) +
      scalaFileText
  }


  def createFileWithAdditionalImports(codeWithContext: CodeWithContext): Option[PsiJavaFile] = {
    codeWithContext
      .javaFile
      .map(new AdditioinalImportsResolver(_).addImports())
  }

  sealed class CopyContext(val prefix: String, val postfix: String)

  case class FileContext() extends CopyContext("", "")

  case class ClassContext() extends CopyContext("class Dummy { ", s"${lineSeparator()}}")

  case class BlockContext() extends CopyContext("class Dummy { void foo () { ", s"${lineSeparator()}}${lineSeparator()}}")

  case class ExpressionContext() extends CopyContext("class Dummy { Object field =", s"${lineSeparator()}}")

  case class CodeWithContext(text: String, project: Project, context: CopyContext) {
    def parseWithContextAsJava: Boolean =
      PlainTextCopyUtil.isValidJavaFile(context.prefix + text + context.postfix, project)

    def javaFile: Option[PsiJavaFile] =
      PlainTextCopyUtil.createJavaFile(context.prefix + text + context.postfix, project)
  }

  def computejavaContext(text: String, project: Project): Option[CodeWithContext] = {
    val asFile = CodeWithContext(text, project, FileContext())
    val asClass = CodeWithContext(text, project, ClassContext())
    val asBlock = CodeWithContext(text, project, BlockContext())
    val asExpression = CodeWithContext(text, project, ExpressionContext())

    if (asFile.parseWithContextAsJava) Some(asFile)
    else if (asClass.parseWithContextAsJava) Some(asClass)
    else if (asBlock.parseWithContextAsJava) Some(asBlock)
    else if (asExpression.parseWithContextAsJava) Some(asExpression)
    else None
  }
}
