package org.jetbrains.plugins.scala
package util.macroDebug

import java.io.File
import scala.meta.internal.{ast => m}
import scala.meta.eval._

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory, _}
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScMethodCallImpl
import org.jetbrains.plugins.scala.meta.trees.ConverterImpl
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.meta.internal.ast

/**
 * User: Dmitry Naydanov
 * Date: 11/5/12
 */
object ScalaMacroDebuggingUtil {
  private[this] val MACRO_DEBUG_ENABLE_PROPERTY = "scala.macro.debug.enabled"

  val MACRO_SIGN_PREFIX = "<[[macro:" //=\
  val needFixCarriageReturn = SystemInfo.isWindows
  val isEnabled = true

  private[this] val SOURCE_FILE_NAME = new FileAttribute("PreimageFileName", 1, false)
  private[this] val SYNTHETIC_SOURCE_ATTRIBUTE = new FileAttribute("SyntheticMacroCode", 1, false)
  private[this] val SOURCE_CACHE = mutable.HashMap[String, PsiFile]()
  private[this] val SYNTHETIC_OFFSETS_MAP = mutable.HashMap[String, List[(Int, Int, Int)]]()
  private[this] val UPDATE_QUEUE = mutable.HashSet[String]()
  private[this] val MARKERS_CACHE = mutable.HashMap[String, Int]()
  private[this] val PREIMAGE_CACHE = mutable.HashMap[PsiFile, PsiFile]()

  val macrosToExpand = new mutable.HashSet[PsiElement]()
  val allMacroCalls = new mutable.HashSet[PsiElement]()

  def saveCode(fileName: String, code: java.util.ArrayList[String]) {
    import scala.collection.JavaConversions._

    if (!isEnabled) return
    val file = VfsUtil.findFileByIoFile(new File(fileName stripPrefix MACRO_SIGN_PREFIX), true)

    val dataStream = SYNTHETIC_SOURCE_ATTRIBUTE writeAttribute file
    code foreach (dataStream writeUTF _.stripPrefix(MACRO_SIGN_PREFIX))
    dataStream flush()
    dataStream close()

    UPDATE_QUEUE += file.getCanonicalPath
  }

  def loadCode(file: PsiFile, force: Boolean = false): PsiFile = {
    if (!isEnabled || file.getVirtualFile.isInstanceOf[LightVirtualFile]) return null

    val canonicalPath = file.getVirtualFile.getCanonicalPath

    def createFile(): PsiFile = {
      val dataStream = SYNTHETIC_SOURCE_ATTRIBUTE readAttribute file.getVirtualFile
      if (dataStream == null) return null

      var line = dataStream readUTF()
      val linesRed = StringBuilder.newBuilder

      while (line != null && dataStream.available() > 0) {
        linesRed ++= (line map (c => if (c == 0) ' ' else c)) ++= "\n"
        line = dataStream readUTF()
      }

      //linesRed ++= line
      //unpack debug info 
      val offsets = ListBuffer.empty[(Int, Int, Int)]
      @inline def parse(s: String) = Integer parseInt s
      line split '|' foreach {
        case s =>
          val nums = s split ","
          if (nums.length == 3) {
            offsets.append((parse(nums(0)), parse(nums(1)), parse(nums(2))))
          }
      }
      SYNTHETIC_OFFSETS_MAP += (canonicalPath -> offsets.result())
      // /unpack

      dataStream.close()

      val synFile = PsiFileFactory.getInstance(file.getManager.getProject).
              createFileFromText("expanded_" + file.getName,
                ScalaFileType.SCALA_FILE_TYPE, linesRed.toString(), file.getModificationStamp, true).asInstanceOf[ScalaFile]

      SOURCE_CACHE += (canonicalPath -> synFile)
      PREIMAGE_CACHE += (synFile -> file)

      synFile
    }

    if (force || UPDATE_QUEUE.remove(canonicalPath)) createFile() else SOURCE_CACHE get canonicalPath getOrElse createFile()
  }

  def readPreimageName(file: PsiFile): Option[String] =
    Option(SOURCE_FILE_NAME readAttributeBytes file.getVirtualFile) map (new String(_))

  def getPreimageFile(file: PsiFile) = PREIMAGE_CACHE get file

  def isLoaded(file: PsiFile) = SOURCE_CACHE get file.getVirtualFile.getCanonicalPath match {
    case Some(_) => true
    case _ => false
  }

  def tryToLoad(file: PsiFile) = !file.getVirtualFile.isInstanceOf[LightVirtualFile] &&
          (isLoaded(file) || loadCode(file, false) != null)

  def getOffsets(file: PsiFile) = SYNTHETIC_OFFSETS_MAP get file.getVirtualFile.getCanonicalPath

  def getOffsetsCount(file: PsiFile) = SYNTHETIC_OFFSETS_MAP get file.getVirtualFile.getCanonicalPath match {
    case Some(offsets) => offsets.length
    case _ => 0
  }

  def checkMarkers(fileName: String, markersCount: Int) = MARKERS_CACHE get fileName match {
    case Some(oldCount) => if (oldCount == markersCount) { false } else {
      MARKERS_CACHE += (fileName -> markersCount); true
    }
    case None => MARKERS_CACHE += (fileName -> markersCount); true
  }

  def isMacroCall(element: PsiElement) = {
    val sres = element match {
      case methodInvocation: MethodInvocation => methodInvocation.getEffectiveInvokedExpr match {
        case ref: ScReferenceExpression => ref.resolve() match {
          case _: ScMacroDefinition => true
          case _ => false
        }
        case _ => false
      }
      case _ => false
    }
    sres || isImplicitMacro(element)
  }

  def isImplicitMacro(element: PsiElement) = element match {
    case methodInvocation: MethodInvocation => methodInvocation.getEffectiveInvokedExpr match {
      case ref: ScReferenceExpression => ref.resolve() match {
        case fun: ScFunctionDefinition
          if fun.name.endsWith("apply") && fun.paramClauses.clauses.exists(_.parameters.exists(_.isImplicitParameter)) => true
        case _ => false
      }
      case _ => false
    }
    case _ => false
  }

  def copyTextBetweenEditors(from: Editor, to: Editor, project: Project) {
    extensions.inWriteAction {
      val toDoc: Document = to.getDocument
      toDoc.setText(from.getDocument.getText)
      PsiDocumentManager.getInstance(project).commitDocument(toDoc)
    }
  }

  def expandMacros(project: Project) {
    val sourceEditor = FileEditorManager.getInstance(project).getSelectedTextEditor
    val macroEditor = WorksheetEditorPrinter.newMacrosheetUiFor(sourceEditor,
      PsiDocumentManager.getInstance(project).getPsiFile(sourceEditor.getDocument).getVirtualFile).getViewerEditor
    val macrosheetFile = PsiDocumentManager.getInstance(project).getPsiFile(macroEditor.getDocument)

    copyTextBetweenEditors(sourceEditor, macroEditor, project)

    for (elt <- macrosToExpand.toList.sortWith((a,b) => a.getTextOffset > b.getTextOffset)) {
      var macroCall = macrosheetFile.findElementAt(elt.getTextOffset)
      while (macroCall != null && !ScalaMacroDebuggingUtil.isMacroCall(macroCall)) {
        macroCall = macroCall.getParent
      }
      if (macroCall != null) {
        //        extensions.inWriteAction {
        WriteCommandAction.runWriteCommandAction(project, new Runnable {
          override def run() {
            implicit val context = new org.jetbrains.plugins.scala.meta.semantic.Context(macroCall.getProject)
            val callImpl = macroCall.asInstanceOf[ScMethodCallImpl]
            val body = callImpl.getEffectiveInvokedExpr match {
              case e: ScReferenceExpression => e.resolve().asInstanceOf[ScMacroDefinition]
            }
            val macroBody = ConverterImpl.ideaToMeta(body)
            val macroArgs = callImpl.args.exprs.toStream.map(ConverterImpl.ideaToMeta)
            val macroApplication = m.Term.Apply(m.Term.Name(body.name), macroArgs.asInstanceOf[scala.collection.immutable.Seq[m.Term]])
            val mMacroEnv = scala.collection.mutable.Map[m.Term.Name, Any]()
            try {
              val result = macroApplication.eval(mMacroEnv.toMap)
            } catch {
              case ex: Exception =>
                val v = ex.getMessage
                ex.printStackTrace()
                ""
              case ex: Throwable =>
                val v = ex.getMessage
                ex.printStackTrace()
                ""
            }
            val macroExpansion =
              """
                |val eval$1: String = "world"
                |print("hello ")
                |print(eval$1)
                |print("!")
                |()
              """.stripMargin
            val expansion = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(s"{$macroExpansion}", PsiManager.getInstance(project))
            var statement = macroCall.getParent.addAfter(expansion, macroCall)
            macroCall.delete()
            statement = CodeStyleManager.getInstance(project).reformat(statement)
          }
        })
      }
    }
  }

}
