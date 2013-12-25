package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.execution.configurations._
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.openapi.project.Project
import java.lang.String
import org.jdom.Element
import com.intellij.openapi.options.SettingsEditor
import java.io._
import collection.JavaConversions._
import com.intellij.execution.runners.{ProgramRunner, ExecutionEnvironment}
import com.intellij.openapi.util._
import com.intellij.execution._
import com.intellij.execution.process.{ProcessHandler, ProcessEvent, ProcessAdapter}
import ui.ConsoleViewContentType
import com.intellij.ide.util.EditorHelper
import com.intellij.psi._
import extensions._
import com.intellij.openapi.editor.{EditorFactory, Document, Editor}
import com.intellij.psi.impl.PsiManagerEx
import java.util
import worksheet.WorksheetFoldingBuilder
import com.intellij.lang.ASTNode
import lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import collection.mutable
import com.intellij.execution.impl.ConsoleViewImpl
import java.awt.event._
import java.awt.{Container, Dimension, BorderLayout}
import com.intellij.openapi.editor.impl.EditorImpl
import settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScIfStmt, ScMethodCall, ScInfixExpr}
import com.intellij.ui.JBSplitter
import lang.psi.api.toplevel.imports.ScImportStmt
import javax.swing.{JLayeredPane, JComponent}
import lang.psi.api.ScalaFile
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.runner.BaseRunConfiguration

/**
 * @author Ksenia.Sautina
 * @since 10/15/12
 */
class WorksheetRunConfiguration(project: Project, configurationFactory: ConfigurationFactory, name: String)
  extends BaseRunConfiguration(project, configurationFactory, name) {
  val mainClass = "org.jetbrains.plugins.scala.worksheet.WorksheetRunner"
  val END_MESSAGE = "Output exceeds cutoff limit.\n"

  val ContinueString = "     | "
  val PromptString   = "scala> "

  var worksheetField = ""

  def apply(params: WorksheetRunConfigurationForm) {
    javaOptions = params.getJavaOptions
    consoleArgs = params.getWorksheetOptions
    workingDirectory = params.getWorkingDirectory
    worksheetField = params.getWorksheetField
    setModule(params.getModule)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val lineNumbers = new util.ArrayList[Int]()
    var currentIndex = -1
    var addedLinesCount = 0
    var isFirstLine = true

    def createWorksheetViewer(editor: Editor, virtualFile: VirtualFile): Editor = {
      val editorComponent = editor.getComponent

      val prop = if (editorComponent.getComponentCount > 0 && editorComponent.getComponent(0).isInstanceOf[JBSplitter])
        editorComponent.getComponent(0).asInstanceOf[JBSplitter].getProportion else 0.5f
      val dimension: Dimension = editorComponent.getSize
      val prefDim = new Dimension((dimension.getWidth / 2).toInt, dimension.getHeight.toInt)
      editor.getSettings.setFoldingOutlineShown(false)

      val worksheetViewer = if (WorksheetViewerInfo.getViewer(editor) == null)
        createBlankEditor(project).asInstanceOf[EditorImpl]
      else
        WorksheetViewerInfo.getViewer(editor).asInstanceOf[EditorImpl]
      worksheetViewer.getComponent.setPreferredSize(prefDim)

      val gutter: EditorGutterComponentEx = worksheetViewer.getGutterComponentEx
      if (gutter != null && gutter.getParent != null) gutter.getParent.remove(gutter)

      worksheetViewer.getScrollPane.getVerticalScrollBar.setModel(editor.asInstanceOf[EditorImpl].getScrollPane.getVerticalScrollBar.getModel)
      editor.getContentComponent.setPreferredSize(prefDim)

      val child: Container = editorComponent.getParent
      val parent: Container = child.getParent

      if  (parent.getComponents.size > 1 && parent.getComponent(1).isInstanceOf[JBSplitter]) {
        val pane = parent.getComponent(1).asInstanceOf[JBSplitter]
        pane.setSecondComponent(worksheetViewer.getComponent)
      } else {
        val pane = new JBSplitter(false, prop)

        pane.setSecondComponent(worksheetViewer.getComponent)
        if (parent.isInstanceOf[JLayeredPane]) {
          parent.remove(child)
          pane.setFirstComponent(child.getComponent(0).asInstanceOf[JComponent])
          parent.add(pane, BorderLayout.CENTER)
        }
        else if (child.isInstanceOf[JLayeredPane]) {
          child.remove(editorComponent)
          pane.setFirstComponent(editorComponent)
          child.add(pane, BorderLayout.CENTER)
        }
      }

      WorksheetViewerInfo.addViewer(worksheetViewer, editor)
      worksheetViewer
    }


    def printResults(s: String, editor: Editor, worksheetViewer: Editor) {
      invokeLater {
        inWriteAction {
          if (s.startsWith(PromptString)) {
            isFirstLine = true
            currentIndex = currentIndex + 1
          } else {
            addWorksheetEvaluationResults(s, editor, worksheetViewer)
            isFirstLine = false
          }
        }
      }
    }

    def addWorksheetEvaluationResults(s: String, editor: Editor, worksheetViewer: Editor) {
      val SHIFT = ScalaProjectSettings.getInstance(project).getShift
      val document = editor.getDocument
      val worksheetViewerDocument = worksheetViewer.getDocument
      val buffer = new mutable.StringBuilder()

      val currentLine = if (currentIndex > -1 && currentIndex < lineNumbers.length)
        lineNumbers(currentIndex) + addedLinesCount
      else -1

      if (currentLine > -1) {
        var lineNumber = if (currentIndex == 0) worksheetViewerDocument.getLineCount else worksheetViewerDocument.getLineCount - 1
        while (currentLine > lineNumber) {
          worksheetViewerDocument.insertString(worksheetViewerDocument.getTextLength, "\n")
          lineNumber = lineNumber + 1
        }

        if (isFirstLine) {
          buffer.append(WorksheetFoldingBuilder.FIRST_LINE_PREFIX).append(" ").append(s)
        } else {
          buffer.append(WorksheetFoldingBuilder.LINE_PREFIX).append(" ").append(s)

          val offset = if (document.getLineEndOffset(currentLine) + 1 > document.getTextLength) document.getTextLength
          else document.getLineEndOffset(currentLine) + 1
          document.insertString(offset, "\n")
          addedLinesCount = addedLinesCount + 1
        }

        if (s.length > SHIFT) {
          var index =  SHIFT + WorksheetFoldingBuilder.FIRST_LINE_PREFIX.length + 1
          while (index < buffer.length) {
            buffer.insert(index, "\n" + WorksheetFoldingBuilder.LINE_PREFIX + " ")
            index = index + SHIFT + WorksheetFoldingBuilder.LINE_PREFIX.length
            val offset = if (document.getLineEndOffset(currentLine) + 1 > document.getTextLength) document.getTextLength
            else document.getLineEndOffset(currentLine) + 1
            document.insertString(offset, "\n")
            addedLinesCount = addedLinesCount + 1
          }
        }

        worksheetViewerDocument.insertString(worksheetViewerDocument.getTextLength, buffer.toString())
        PsiDocumentManager.getInstance(project).commitDocument(worksheetViewerDocument)

        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }

    def cleanWorksheet(node: ASTNode, document: Document, editor: Editor) {
      currentIndex = -1
      isFirstLine = true
      lineNumbers.clear()
      addedLinesCount = 0

      val wvDocument = WorksheetViewerInfo.getViewer(editor).getDocument
      try {
        if (wvDocument != null && wvDocument.getLineCount > 0) {
          for (i <- wvDocument.getLineCount - 1 to 0 by -1) {
            val wStartOffset = wvDocument.getLineStartOffset(i)
            val wEndOffset = wvDocument.getLineEndOffset(i)

            val wCurrentLine = wvDocument.getText(new TextRange(wStartOffset, wEndOffset))
            if (wCurrentLine.trim != "" && wCurrentLine.trim != "\n" && i < document.getLineCount) {
              val eStartOffset = document.getLineStartOffset(i)
              val eEndOffset = document.getLineEndOffset(i)
              val eCurrentLine = document.getText(new TextRange(eStartOffset, eEndOffset))

              if ((eCurrentLine.trim == "" || eCurrentLine.trim == "\n") && eEndOffset + 1 < document.getTextLength) {
                document.deleteString(eStartOffset, eEndOffset + 1)
                PsiDocumentManager.getInstance(project).commitDocument(document)
              }
            }
          }
        }
      } finally {
        if (wvDocument != null && !project.isDisposed) {
          wvDocument.setText("")
          PsiDocumentManager.getInstance(project).commitDocument(wvDocument)
        }
      }
    }

    def evaluateWorksheet(psiFile: ScalaFile, processHandler: ProcessHandler, editor: Editor) {
      def deleteComments(text: String): String = {
        val list = text.split("\n")
        var result = new StringBuffer
        for (l <- list) {
          if (!l.startsWith("//")) {
            result = result.append(l).append("\n")
          }
        }
        result.toString
      }

      invokeLater {
        inWriteAction {
          val document = editor.getDocument
          cleanWorksheet(psiFile.getNode, document, editor)
          val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
          var isObject = false
          var myObject: ScObject = null
          var classAndObjectCount = 0
          val imports = new util.ArrayList[ScImportStmt]()

          file.getChildren.foreach {
            case obj: ScObject =>
              isObject = true
              myObject = obj
              classAndObjectCount += 1
            case clazz: ScClass => classAndObjectCount += 1
            case imp: ScImportStmt if imp.getText.trim != "" => imports.add(imp)
            case another if another.getText.trim != "" => classAndObjectCount += 1
            case _ =>
          }

          def processElement(child: PsiElement) {
            val trimmed = child.getText.trim

            if (trimmed != "" && trimmed != "\n" && !child.isInstanceOf[PsiComment]) {
              val outputStream: OutputStream = processHandler.getProcessInput
              try {
                val textBuilder = new StringBuilder

                def checkChildren(elem: PsiElement) {
                  elem.getNode.getChildren(null).map(_.getPsi).foreach {
                    case whitespace: PsiWhiteSpace if whitespace.getText.contains("\n") =>
                      val count = whitespace.getText.count(_ == '\n')
                      if (count < 2) {
                        whitespace.getParent match {
                          case _: ScInfixExpr | _: ScMethodCall | _: ScIfStmt => textBuilder.append(" ")
                          case _: ScClass | _: ScObject => whitespace.getNextSibling match {
                            case extBlock: ScExtendsBlock => textBuilder.append(" ")
                            case _ => textBuilder.append(whitespace.getText)
                          }
                          case _ => textBuilder.append(whitespace.getText)
                        }
                      } else textBuilder.append(whitespace.getText)
                    case child: LeafPsiElement => textBuilder.append(child.getText)
                    case otherPsi => checkChildren(otherPsi)
                  }
                }

                checkChildren(child)
                val text = textBuilder.toString()
                lineNumbers.add(document.getLineNumber(child.getTextRange.getEndOffset))
                val result = deleteComments(text)
                val bytes: Array[Byte] = result.getBytes
                outputStream.write(bytes)
                outputStream.flush()
              }
              catch {
                case ignore: IOException =>
              }
            }
          }
          if (!isObject || classAndObjectCount > 1) {
            file.getChildren.foreach(processElement)
          } else if (isObject && classAndObjectCount == 1) {
            for (im <- imports) {
              val outputStream: OutputStream = processHandler.getProcessInput
              try {
                lineNumbers.add(document.getLineNumber(im.getTextRange.getEndOffset))
                val bytes: Array[Byte] = (im.getText.trim + "\n").getBytes
                outputStream.write(bytes)
                outputStream.flush()
              }
              catch {
                case e: IOException => //ignore
              }
            }
            myObject.getChildren.foreach(child => {
              if (child.isInstanceOf[ScExtendsBlock]) {
                child.getChildren.foreach(child => {
                  if (child.isInstanceOf[ScTemplateBody]) {
                    child.getChildren.foreach(processElement)
                  }
                })
              }
            })
          }

          endProcess(processHandler)
        }
      }
    }

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = createParams
        params.getProgramParametersList.addParametersString(worksheetField)
        params.getProgramParametersList.addParametersString(consoleArgs)
        params
      }

      override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
        val file = new File(worksheetField)
        if (file == null) throw new RuntimeConfigurationException("Worksheet is not specified: file doesn't exist.")

        val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)
        if (virtualFile == null) throw new ExecutionException("Worksheet is not specified. File is not found.")

        val psiFile: PsiFile = PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].getFileManager.getCachedPsiFile(virtualFile)
        if (psiFile == null) throw new RuntimeConfigurationException("Worksheet is not specified: there is no cached file.")

        val processHandler = startProcess
        val runnerSettings = getRunnerSettings
        JavaRunConfigurationExtensionManager.getInstance.attachExtensionsToProcess(WorksheetRunConfiguration.this, processHandler, runnerSettings)

        val editor = EditorHelper.openInEditor(psiFile)

        val worksheetViewer = createWorksheetViewer(editor, virtualFile)
        val worksheetConsoleView = new ConsoleViewImpl(project, false)
        evaluateWorksheet(psiFile.asInstanceOf[ScalaFile], processHandler, editor)

        val myProcessListener: ProcessAdapter = new ProcessAdapter {
          var resultsCount = 0
          var chunksReceived = 0

          @inline private[this] def closeHandler() {
            endProcess(processHandler)
            processHandler.removeProcessListener(this)
          }

          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            if (ConsoleViewContentType.NORMAL_OUTPUT == ConsoleViewContentType.getConsoleViewType(outputType) &&
              worksheetViewer != null && text.trim != "" && !text.startsWith(ContinueString)) {

              if (text.startsWith(PromptString)) {
                resultsCount += 1
                chunksReceived +=  1
              } else resultsCount =1

              if (resultsCount >  ScalaProjectSettings.getInstance(project).getOutputLimit) {
                printResults(END_MESSAGE, editor, worksheetViewer)
                closeHandler()
              } else {
                printResults(text, editor, worksheetViewer)
                if (chunksReceived > lineNumbers.size()) closeHandler()
              }
            }
          }
        }

        processHandler.addProcessListener(myProcessListener)

        editor.getContentComponent.addKeyListener(new KeyListener() {
          override def keyReleased(e: KeyEvent) {
            if (e.getKeyCode == KeyEvent.VK_ENTER) {
              invokeLater {
                inWriteAction {
                  val worksheetViewerDocument = WorksheetViewerInfo.getViewer(editor).getDocument
                  worksheetViewerDocument.insertString(worksheetViewerDocument.getTextLength, "\n")
                  PsiDocumentManager.getInstance(project).commitDocument(worksheetViewerDocument)
                }
              }
            }
          }

          override def keyTyped(e: KeyEvent) {
          }

          override def keyPressed(e: KeyEvent) { //wtf???
//            endProcess(processHandler)
//            processHandler.removeProcessListener(myProcessListener)
          }
        })

        val res = new DefaultExecutionResult(worksheetConsoleView, processHandler,
          createActions(worksheetConsoleView, processHandler, executor): _*)
        res
      }
    }

    state
  }

  def endProcess(processHandler: ProcessHandler) {
    val outputStream: OutputStream = processHandler.getProcessInput
    try {
      val text = ":quit"
      val bytes: Array[Byte] = (text + "\n").getBytes
      outputStream.write(bytes)
      outputStream.flush()
    }
    catch {
      case e: IOException => //ignore
    }
  }

  def createBlankEditor(project: Project): Editor = {
    val factory: EditorFactory = EditorFactory.getInstance
    val document = factory.createDocument("")
    val editor: Editor = factory.createViewer(document, project)
    editor.setBorder(null)
    editor
  }

  override def checkConfiguration() {
    super.checkConfiguration()

    val file = new File(worksheetField)

    if (file == null) {
      throw new RuntimeConfigurationException("Worksheet is not specified: file does not exist.")
    }

    val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

    if (virtualFile == null) {
      throw new RuntimeConfigurationException("Worksheet is not specified: file is not found.")
    }

    val psiFile: PsiFile = PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].getFileManager.getCachedPsiFile(virtualFile)

    if (psiFile == null) {
      throw new RuntimeConfigurationException("Worksheet is not specified: there is no cached file.")
    }

    if (getModule == null) {
      throw new RuntimeConfigurationException("Module is not specified")
    }

    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this)
  }

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new WorksheetRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "worksheetOptions", consoleArgs)
    JDOMExternalizer.write(element, "worksheetField", worksheetField)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    consoleArgs = JDOMExternalizer.readString(element, "worksheetOptions")
    val ws = JDOMExternalizer.readString(element, "worksheetField")
    if (ws != null)
      worksheetField = ws
  }

}
