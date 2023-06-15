package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.conversion.ConverterUtil.ConvertedCode
import org.jetbrains.plugins.scala.conversion.copy.ScalaPasteFromJavaDialog.CopyFrom
import org.jetbrains.plugins.scala.conversion.copy.plainText.TextJavaCopyPastePostProcessor._
import org.jetbrains.plugins.scala.conversion.copy.{ScalaPasteFromJavaDialog, SingularCopyPastePostProcessor}
import org.jetbrains.plugins.scala.conversion.{ConverterUtil, JavaToScala}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

import java.awt.datatransfer.{DataFlavor, Transferable}

final class TextJavaCopyPastePostProcessor extends SingularCopyPastePostProcessor[ConverterUtil.ConvertedCode](DataFlavor.stringFlavor) {

  override protected def collectTransferableData(
    startOffsets: Array[Int],
    endOffsets: Array[Int]
  )(implicit file: PsiFile, editor: Editor): Option[ConverterUtil.ConvertedCode] = None

  override protected def extractTransferableDataImpl(content: Transferable): Option[AnyRef] = {
    def hasTextBlockTransferableData(content: Transferable) =
      content.getTransferDataFlavors
        .map(_.getRepresentationClass)
        .exists(classOf[TextBlockTransferableData].isAssignableFrom)

    val isPlainTextCopy =
      ApplicationManager.getApplication.isUnitTestMode && !TextJavaCopyPastePostProcessor.insideIde ||
        !hasTextBlockTransferableData(content)

    if (isPlainTextCopy) {
      super.extractTransferableDataImpl(content).map { text =>
        ConvertedCode(text = text.asInstanceOf[String])
      }
    }
    else None
  }

  override protected def processTransferableData(
    bounds: RangeMarker,
    caretOffset: Int,
    ref: Ref[_ >: java.lang.Boolean],
    value: ConvertedCode
  )(implicit
    project: Project,
    editor: Editor,
    file: ScalaFile
  ): Unit = {
    val settings: ScalaProjectSettings = ScalaProjectSettings.getInstance(project)
    if (!settings.isEnableJavaToScalaConversion)
      return

    val ConvertedCode(_, text, _) = value
    if (text == null || text == "")
      return

    val module = file.module match {
      case Some(m) => m
      case None =>
        return
    }
    if (PlainTextCopyUtil.isValidScalaFile(text, module))
      return

    // TODO: Collect available imports in current scope. Use them while converting
    val javaCodeWithContextOpt = computeJavaCodeWithCopyContext(text)
    javaCodeWithContextOpt.foreach { javaCodeWithContext =>
      val proceedWithConversion = ScalaPasteFromJavaDialog.showAndGet(CopyFrom.Text, project)
      if (proceedWithConversion) {
        ScalaActionUsagesCollector.logConvertFromJava(project)

        inWriteAction {
          val javaFileOpt = javaCodeWithContext.createJavaFile
          javaFileOpt.foreach { javaFile =>
            //remove java pasted java code from file for treating file as a valid scala file
            //it needs for SCL-11425
            ConverterUtil.performePaste(editor, bounds, " " * (bounds.getEndOffset - bounds.getStartOffset), project)

            val convertedText = convert(javaFile, javaCodeWithContext.context)
            ConverterUtil.performePaste(editor, bounds, convertedText, project)

            CodeStyleManager.getInstance(project)
              .reformatText(
                PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument),
                bounds.getStartOffset, bounds.getStartOffset + convertedText.length
              )

            ConverterUtil.cleanCode(file, project, bounds.getStartOffset, bounds.getEndOffset)
          }
        }
      }
    }
  }
}

object TextJavaCopyPastePostProcessor {
  @TestOnly
  var insideIde: Boolean = true

  private sealed class CopyContext(val prefix: String, val postfix: String)
  private object CopyContext {
    case class FileContext() extends CopyContext("", "")
    case class ClassContext() extends CopyContext("class Dummy { ", "\n}")
    case class BlockContext() extends CopyContext("class Dummy { void foo () { ", "\n}\n}")
    case class ExpressionContext() extends CopyContext("class Dummy { Object field =", "\n}")
  }

  private case class CodeWithCopyContext(text: String, context: CopyContext)
                                        (implicit project: Project) {
    def canBeValidJavaFile: Boolean =
      PlainTextCopyUtil.isValidJavaFile(context.prefix + text + context.postfix)

    def createJavaFile: Option[PsiJavaFile] =
      PlainTextCopyUtil.createJavaFile(context.prefix + text + context.postfix)
  }

  private def convert(javaFile: PsiJavaFile, context: CopyContext): String = {
    def convertStatement(psiElement: PsiElement): String =
      Option(psiElement).map(holder => JavaToScala.convertPsisToText(Array(holder))).getOrElse("")

    def withNewLine(text: String): String = if (text == "") text else text + "\n"

    val javaFileLen = javaFile.getTextLength
    val (begin, end) = context match {
      case _: CopyContext.FileContext => (0, javaFileLen)
      case part =>
        (part.prefix.length + javaFile.getImportList.getTextRange.getEndOffset,
          javaFileLen - part.prefix.length)
    }

    val topElements = ConverterUtil.collectTopElements(begin, end, javaFile)
    val elementsToConvert = topElements.filterNot(_.is[PsiImportList, PsiPackageStatement])

    val scalaFileText = JavaToScala.convertPsisToText(elementsToConvert, textMode = true)

    withNewLine(convertStatement(javaFile.getPackageStatement)) +
      convertStatement(javaFile.getImportList) +
      scalaFileText
  }

  private def computeJavaCodeWithCopyContext(text: String)(implicit project: Project): Option[CodeWithCopyContext] = {
    import CopyContext._

    val asFile = CodeWithCopyContext(text, FileContext())
    val asClass = CodeWithCopyContext(text, ClassContext())
    val asBlock = CodeWithCopyContext(text, BlockContext())
    val asExpression = CodeWithCopyContext(text, ExpressionContext())

    if (asFile.canBeValidJavaFile) Some(asFile)
    else if (asClass.canBeValidJavaFile) Some(asClass)
    else if (asBlock.canBeValidJavaFile) Some(asBlock)
    else if (asExpression.canBeValidJavaFile) Some(asExpression)
    else None
  }
}