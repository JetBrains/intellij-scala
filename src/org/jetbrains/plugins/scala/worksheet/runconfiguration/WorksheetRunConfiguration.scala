package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.execution.configurations._
import com.intellij.openapi.vfs.{LocalFileSystem, JarFileSystem, VirtualFile}
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import java.lang.String
import org.jdom.Element
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.openapi.projectRoots.{JdkUtil, JavaSdkType}
import java.io._
import collection.JavaConversions._
import com.intellij.openapi.roots.{CompilerModuleExtension, OrderRootType, ModuleRootManager}
import com.intellij.execution.runners.{ProgramRunner, ExecutionEnvironment}
import com.intellij.openapi.util._
import com.intellij.execution._
import config.{CompilerLibraryData, Libraries, ScalaFacet}
import compiler.ScalacSettings
import com.intellij.execution.process.{ProcessHandler, ProcessEvent, ProcessAdapter}
import ui.ConsoleViewContentType
import com.intellij.ide.util.EditorHelper
import com.intellij.psi._
import lang.psi.api.ScalaFile
import extensions._
import com.intellij.openapi.editor.{EditorFactory, Document, Editor}
import com.intellij.psi.impl.PsiManagerEx
import java.util
import worksheet.WorksheetFoldingBuilder
import com.intellij.lang.ASTNode
import lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import lang.scaladoc.psi.api.ScDocComment
import lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import collection.mutable
import com.intellij.execution.impl.ConsoleViewImpl
import java.awt.event._
import java.awt.{Dimension, BorderLayout}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.{FileEditorManagerAdapter, FileEditorManagerListener, FileEditorManager}
import settings.ScalaProjectSettings
import scala.Some
import lang.psi.api.expr.{ScMethodCall, ScInfixExpr}
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import lang.psi.api.toplevel.imports.ScImportStmt
import lang.psi.api.statements.ScPatternDefinition

/**
 * @author Ksenia.Sautina
 * @since 10/15/12
 */
class WorksheetRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  val SCALA_HOME = "-Dscala.home="
  val CLASSPATH = "-Denv.classpath=\"%CLASSPATH%\""
  val EMACS = "-Denv.emacs=\"%EMACS%\""
  val MAIN_CLASS = "org.jetbrains.plugins.scala.worksheet.WorksheetRunner"
  val MAX_RESULTS_COUNT = 35
  val END_MESSAGE = "Output exceeds cutoff limit."

  val ContinueString = "     | "
  val PromptString   = "scala> "

  private var javaOptions = "-Djline.terminal=NONE"
  private var workingDirectory = Option(getProject.getBaseDir) map (_.getPath) getOrElse ""

  private var worksheetField = ""

  def getJavaOptions = javaOptions

  def setJavaOptions(s: String) {javaOptions = s}

  def getWorkingDirectory = workingDirectory

  def setWorkingDirectory(s: String) {workingDirectory = s}

  def getWorksheetField = worksheetField

  def setWorksheetField(s: String) {worksheetField = s}

  def apply(params: WorksheetRunConfigurationForm) {
    setJavaOptions(params.getJavaOptions)
    setWorkingDirectory(params.getWorkingDirectory)
    setWorksheetField(params.getWorksheetField)
    setModule(params.getModule)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val lineNumbers = new util.ArrayList[Int]()
    var currentIndex = -1
    var addedLinesCount = 0
    var isFirstLine = true

    def createWorksheetViewer(editor: Editor, virtualFile: VirtualFile): Editor = {
      val prop = if (editor.getComponent.getComponentCount > 0 && editor.getComponent.getComponent(0).isInstanceOf[JBSplitter])
        editor.getComponent.getComponent(0).asInstanceOf[JBSplitter].getProportion else 0.5f
      val dimension = editor.getComponent.getSize()
      val prefDim = new Dimension((dimension.getWidth / 2).toInt, dimension.getHeight.toInt)

      val worksheetViewer = createBlankEditor(project)
      val model = editor.asInstanceOf[EditorImpl].getScrollPane.getVerticalScrollBar.getModel
      worksheetViewer.asInstanceOf[EditorImpl].getScrollPane.getVerticalScrollBar.setModel(model)
      worksheetViewer.getComponent.setPreferredSize(prefDim)

      val editorComponent = editor.getContentComponent
      editorComponent.setPreferredSize(prefDim)
      val pane = new JBSplitter(false, prop)
      val leftPane: JBScrollPane = new JBScrollPane(editorComponent)
      val gutter = editor.asInstanceOf[EditorImpl].getGutterComponentEx
      leftPane.setRowHeaderView(gutter)
      leftPane.setAutoscrolls(true)
      val leftScrollBarModel = leftPane.getVerticalScrollBar.getModel

      worksheetViewer.asInstanceOf[EditorImpl].getScrollPane.getVerticalScrollBar.setModel(leftScrollBarModel)
      worksheetViewer.asInstanceOf[EditorImpl].getGutterComponentEx.getParent.remove(worksheetViewer.asInstanceOf[EditorImpl].getGutterComponentEx)
      pane.setFirstComponent(leftPane)
      pane.setSecondComponent(worksheetViewer.getComponent)

      editor.getComponent.removeAll()
      pane.setPreferredSize(dimension)
      editor.getComponent.add(pane, BorderLayout.CENTER)

      WorksheetViewerInfo.addViewer(worksheetViewer, editor)
      worksheetViewer
    }

    def printResults(s: String, contentType: ConsoleViewContentType, editor: Editor, worksheetViewer: Editor) {
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

        WorksheetRunConfiguration.wvDocument = worksheetViewerDocument
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }

    def cleanWorksheet(node: ASTNode, document: Document) {
      currentIndex = -1
      isFirstLine = true
      lineNumbers.clear()
      addedLinesCount = 0

      val wvDocument = WorksheetRunConfiguration.wvDocument
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
      invokeLater {
        inWriteAction {
          val document = editor.getDocument
          cleanWorksheet(psiFile.getNode, document)
          val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
          var isObject = false
          var myObject: ScObject = null
          var classAndObjectCount = 0
          val imports = new util.ArrayList[ScImportStmt]()

          for (ch <- file.getChildren) {
            if (ch.isInstanceOf[ScObject]) {
              isObject = true
              myObject = ch.asInstanceOf[ScObject]
              classAndObjectCount = classAndObjectCount + 1
            } else if (ch.isInstanceOf[ScClass]) {
              classAndObjectCount = classAndObjectCount + 1
            } else if (ch.getText.trim != "" && !ch.isInstanceOf[ScImportStmt]) {
              classAndObjectCount = classAndObjectCount + 1
            } else if (ch.getText.trim != "" && ch.isInstanceOf[ScImportStmt]) {
              imports.add(ch.asInstanceOf[ScImportStmt])
            }
          }

          if (!isObject || classAndObjectCount > 1) {
            file.getChildren.foreach(child => {
              if (child.getText.trim != "" && child.getText.trim != "\n" && (!child.isInstanceOf[PsiComment] && !child.isInstanceOf[ScDocComment])) {
                val outputStream: OutputStream = processHandler.getProcessInput
                try {
                  val text = if (child.isInstanceOf[ScInfixExpr] || child.isInstanceOf[ScMethodCall] ||
                    child.isInstanceOf[ScPatternDefinition])
                    child.getText.trim.replaceAll("\n", " ") else child.getText.trim
                  lineNumbers.add(document.getLineNumber(child.getTextRange.getEndOffset))
                  val bytes: Array[Byte] = (text + "\n").getBytes
                  outputStream.write(bytes)
                  outputStream.flush()
                }
                catch {
                  case e: IOException => //ignore
                }
              }
            })
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
                    child.getChildren.foreach(child => {
                      if (child.getText.trim != "" && child.getText.trim != "\n" && (!child.isInstanceOf[PsiComment] && !child.isInstanceOf[ScDocComment])) {
                        val outputStream: OutputStream = processHandler.getProcessInput
                        try {
                          val text = if (child.isInstanceOf[ScInfixExpr] || child.isInstanceOf[ScMethodCall] ||
                            child.isInstanceOf[ScPatternDefinition])
                            child.getText.trim.replaceAll("\n", " ") else child.getText.trim
                          lineNumbers.add(document.getLineNumber(child.getTextRange.getEndOffset))
                          val bytes: Array[Byte] = (text + "\n").getBytes
                          outputStream.write(bytes)
                          outputStream.flush()
                        }
                        catch {
                          case e: IOException => //ignore
                        }
                      }
                    })
                  }
                })
              }
            })
          }

          endProcess(processHandler)
        }
      }
    }

    val module = getModule
    if (module == null) throw new ExecutionException("Module is not specified")

    val facet = ScalaFacet.findIn(module).getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !(sdk.getSdkType.isInstanceOf[JavaSdkType])) {
      throw CantRunException.noJdkForModule(module)
    }

    val state = new JavaCommandLineState(env) {
      protected override def createJavaParameters: JavaParameters = {
        val params = new JavaParameters()
        params.setJdk(sdk)
        params.setCharset(null)
        params.getVMParametersList.addParametersString(getJavaOptions)
//        params.getVMParametersList.addParametersString("-Xnoagent -Djava.compiler=NONE -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009")

        val files =
          if (facet.fsc) {
            val settings = ScalacSettings.getInstance(getProject)
            val lib: Option[CompilerLibraryData] = Libraries.findBy(settings.COMPILER_LIBRARY_NAME,
              settings.COMPILER_LIBRARY_LEVEL, getProject)
            lib match {
              case Some(lib) => lib.files
              case _ => facet.files
            }
          } else facet.files

        params.getClassPath.addAllFiles(files)

        val rtJarPath = PathUtil.getJarPathForClass(classOf[_root_.org.jetbrains.plugins.scala.worksheet.WorksheetRunner])
        params.getClassPath.add(rtJarPath)
        params.setWorkingDirectory(workingDirectory)
        params.setMainClass(MAIN_CLASS)
        if (JdkUtil.useDynamicClasspath(getProject)) {
          try {
            val fileWithParams: File = File.createTempFile("worksheet", ".tmp")
            val printer: PrintStream = new PrintStream(new FileOutputStream(fileWithParams))
            printer.println("-classpath")
            printer.println(getClassPath(project, facet))
            printer.close()
            params.getProgramParametersList.add("@" + fileWithParams.getPath)
          }
          catch {
            case ignore: IOException => {
            }
          }
        } else {
          params.getProgramParametersList.add("-classpath")
          params.getProgramParametersList.add(getClassPath(project, facet))
          params.getProgramParametersList.addParametersString(worksheetField)
        }
        params
      }

      override def execute(executor: Executor, runner: ProgramRunner[_ <: JDOMExternalizable]): ExecutionResult = {
        val file = new File(getWorksheetField)
        val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)
        if (virtualFile == null) {
          throw new ExecutionException("Worksheet is not specified")
        }
        val psiFile: PsiFile = PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].getFileManager.getCachedPsiFile(virtualFile)

        val processHandler = startProcess
        val runnerSettings = getRunnerSettings
        JavaRunConfigurationExtensionManager.getInstance.attachExtensionsToProcess(WorksheetRunConfiguration.this, processHandler, runnerSettings)

        val editor = EditorHelper.openInEditor(psiFile)
        project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter {
          override def fileClosed(source: FileEditorManager, file: VirtualFile) {
            invokeLater {
              inWriteAction {
                cleanWorksheet(psiFile.getNode, editor.getDocument)
              }
            }
          }
        })

        val worksheetViewer = createWorksheetViewer(editor, virtualFile)
        val worksheetConsoleView = new ConsoleViewImpl(project, false)
        evaluateWorksheet(psiFile.asInstanceOf[ScalaFile], processHandler, editor)

        var results_count = 0
        val myProcessListener: ProcessAdapter = new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            if (ConsoleViewContentType.NORMAL_OUTPUT == ConsoleViewContentType.getConsoleViewType(outputType) &&
              worksheetViewer != null && text.trim != "" && !text.startsWith(ContinueString)) {
              if (text.startsWith(PromptString)) {
                results_count = 1
              } else {
                results_count = results_count + 1
              }
              if (results_count > MAX_RESULTS_COUNT) {
                printResults(END_MESSAGE, ConsoleViewContentType.NORMAL_OUTPUT, editor, worksheetViewer)
                endProcess(processHandler)
                processHandler.removeProcessListener(this)
              } else {
                printResults(text, ConsoleViewContentType.getConsoleViewType(outputType), editor, worksheetViewer)
              }
            }
          }
        }

        processHandler.addProcessListener(myProcessListener)

        editor.getContentComponent.addKeyListener(new KeyListener(){
          override def keyReleased(e: KeyEvent) {}
          override def keyTyped(e: KeyEvent) {}
          override def keyPressed(e: KeyEvent){
            endProcess(processHandler)
            processHandler.removeProcessListener(myProcessListener)
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
    editor
  }

  override def checkConfiguration() {
    super.checkConfiguration()

    val file = new File(getWorksheetField)

    if (file == null) {
      throw new RuntimeConfigurationException("Worksheet is not specified")
    }

    val virtualFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

    if (virtualFile == null) {
      throw new RuntimeConfigurationException("Worksheet is not specified")
    }

    val psiFile: PsiFile = PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].getFileManager.getCachedPsiFile(virtualFile)

    if (psiFile == null) {
      throw new RuntimeConfigurationException("Worksheet is not specified")
    }

    if (getModule == null) {
      throw new RuntimeConfigurationException("Module is not specified")
    }

    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this)
  }

  def getModule: Module = getConfigurationModule.getModule

  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new WorksheetRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = ScalaFacet.findModulesIn(getProject).toList
  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new WorksheetRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    writeModule(element)
    JDOMExternalizer.write(element, "vmparams4", getJavaOptions)
    JDOMExternalizer.write(element, "workingDirectory", getWorkingDirectory)
    JDOMExternalizer.write(element, "worksheetField", getWorksheetField)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    readModule(element)
    javaOptions = JDOMExternalizer.readString(element, "vmparams4")
    if (javaOptions == null) {
      javaOptions = JDOMExternalizer.readString(element, "vmparams")
      if (javaOptions != null) javaOptions += " -Djline.terminal=NONE"
    }
    val str = JDOMExternalizer.readString(element, "workingDirectory")
    if (str != null)
      workingDirectory = str
    val ws = JDOMExternalizer.readString(element, "worksheetField")
    if (ws != null)
      worksheetField = ws
  }

  private def getClassPath(project: Project, facet: ScalaFacet): String = {
    val pathes: Seq[String] = (for (module <- ModuleManager.getInstance(project).getModules) yield
      getClassPath(module)).toSeq
    pathes.mkString(File.pathSeparator) + File.pathSeparator + getClassPath(facet)
  }

  private def getClassPath(module: Module): String = {
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val entries = moduleRootManager.getOrderEntries
    val cpVFiles = new mutable.HashSet[VirtualFile]
    cpVFiles ++= CompilerModuleExtension.getInstance(module).getOutputRoots(true)
    for (orderEntry <- entries) {
      cpVFiles ++= orderEntry.getFiles(OrderRootType.CLASSES)
    }
    val res = new StringBuilder("")
    for (file <- cpVFiles) {
      var path = file.getPath
      val jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR)
      if (jarSeparatorIndex > 0) {
        path = path.substring(0, jarSeparatorIndex)
      }
      res.append(path).append(File.pathSeparator)
    }
    res.toString()
  }

  private def getClassPath(facet: ScalaFacet): String = {
    val res = new StringBuilder("")
    for (file <- facet.files) {
      var path = file.getPath
      val jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR)
      if (jarSeparatorIndex > 0) {
        path = path.substring(0, jarSeparatorIndex)
      }
      path = PathUtil.getCanonicalPath(path).replace('/', File.separatorChar)
      res.append(path).append(File.pathSeparator)
    }
    res.toString()
  }
}

object WorksheetRunConfiguration {
  var wvDocument: Document = null
  def getDocumentRight = wvDocument
}