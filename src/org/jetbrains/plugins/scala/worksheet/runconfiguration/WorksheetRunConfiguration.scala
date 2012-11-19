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
import collection.mutable.HashSet
import collection.JavaConversions._
import com.intellij.openapi.roots.{CompilerModuleExtension, OrderRootType, ModuleRootManager}
import com.intellij.execution.runners.{ProgramRunner, ExecutionEnvironment}
import com.intellij.openapi.util._
import com.intellij.execution._
import config.{CompilerLibraryData, Libraries, ScalaFacet}
import compiler.ScalacSettings
import com.intellij.execution.process.{ProcessHandler, ProcessEvent, ProcessAdapter}
import ui.ConsoleViewContentType
import worksheet.actions.WorksheetInfo
import com.intellij.ide.util.EditorHelper
import com.intellij.psi._
import lang.psi.api.ScalaFile
import extensions._
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.impl.PsiManagerEx
import java.util
import worksheet.WorksheetFoldingBuilder
import settings.ScalaProjectSettings
import com.intellij.lang.ASTNode
import scala.Some
import lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import lang.scaladoc.psi.api.ScDocComment
import lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import collection.mutable
import com.intellij.execution.impl.ConsoleViewImpl
import java.awt.event.{KeyEvent, KeyListener}

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

  val ContinueString = "     | "
  val PromptString   = "scala> "

  private var javaOptions = "-Djline.terminal=NONE"
  private var workingDirectory = {
    val base = getProject.getBaseDir
    if (base != null) base.getPath
    else ""
  }

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
    val offsets = new util.ArrayList[Int]()
    var shifts = 0
    var offset = 0
    var offsetIndex = -1
    var isFirstLine = true

    def printResults(s: String, contentType: ConsoleViewContentType, editor: Editor) {
      if (ConsoleViewContentType.NORMAL_OUTPUT == contentType && editor != null &&
        s.trim != "" && !s.startsWith(ContinueString)) {
        invokeLater {
          inWriteAction {
            if (s.startsWith(PromptString)) {
              offsetIndex = offsetIndex + 1
              isFirstLine = true
            } else {
              addWorksheetEvaluationResults(s, editor)
              isFirstLine = false
            }
          }
        }
      }
    }

    def addWorksheetEvaluationResults(s: String, editor: Editor) {
      val document = editor.getDocument
      val left_indent = ScalaProjectSettings.getInstance(getProject).getLeftIndent
      val SHIFT = ScalaProjectSettings.getInstance(getProject).getShift
      val buffer = new mutable.StringBuilder()
      if (offsets.length > 0 && offsetIndex < offsets.length) {
        offset = offsets.get(offsetIndex) + shifts
        val line = document.getLineNumber(offset)
        val spaceCount = left_indent - (offset - document.getLineStartOffset(line))
        if (spaceCount < 1)   {
          buffer.append("\n")
          for (i <- 1 to left_indent) {
            buffer.append(" ")
          }
        }
        if (isFirstLine) buffer.append(WorksheetFoldingBuilder.FIRST_LINE_PREFIX).append(" ").append(s)
        else buffer.append(WorksheetFoldingBuilder.LINE_PREFIX).append(" ").append(s)
        if (s.length > SHIFT) {
          var index =  left_indent + SHIFT + WorksheetFoldingBuilder.FIRST_LINE_PREFIX.length + 1
          while (index < buffer.length) {
            buffer.insert(index, "\n")
            for (i <- 0 to left_indent) {
              index = index + 1
              buffer.insert(index, " ")
            }
            buffer.insert(index, WorksheetFoldingBuilder.LINE_PREFIX)
            index = index + SHIFT + WorksheetFoldingBuilder.LINE_PREFIX.length
          }
        }
        for (i <- 1 to spaceCount) {
          buffer.insert(0, " ")
        }
        if (buffer.endsWith("\n")) buffer.deleteCharAt(buffer.size - 1)
        document.insertString(offset, buffer.toString())
        shifts = shifts + buffer.toString().length
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
      }
    }

    def evaluateWorksheet(psiFile: ScalaFile, processHandler: ProcessHandler, editor: Editor) {
      def cleanWorksheet(node: ASTNode, document: Document) {
        if (node.getPsi.isInstanceOf[PsiComment] &&
          (node.getText.startsWith(WorksheetFoldingBuilder.FIRST_LINE_PREFIX) || node.getText.startsWith(WorksheetFoldingBuilder.LINE_PREFIX))) {
          val line = document.getLineNumber(node.getPsi.getTextRange.getStartOffset)
          val startOffset = document.getLineStartOffset(line)
          val beginningOfTheLine = document.getText(new TextRange(startOffset, node.getPsi.getTextRange.getStartOffset))
          if (beginningOfTheLine.trim == "") document.deleteString(startOffset, node.getPsi.getTextRange.getEndOffset + 1)
          else document.deleteString(node.getPsi.getTextRange.getStartOffset, node.getPsi.getTextRange.getEndOffset)
          PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        for (child <- node.getChildren(null)) {
          cleanWorksheet(child, document)
        }
      }

      offsetIndex = -1
      offsets.clear()
      shifts = 0
      isFirstLine = true

      val document = editor.getDocument
      invokeLater {
        inWriteAction {
          cleanWorksheet(psiFile.getNode, document)

          val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
          var isObject = false
          var myObject: ScObject = null
          var classAndObjectCount = 0

          for (ch <- file.getChildren) {
            if (ch.isInstanceOf[ScObject]) {
              isObject = true
              myObject = ch.asInstanceOf[ScObject]
              classAndObjectCount = classAndObjectCount + 1
            } else if (ch.isInstanceOf[ScClass]) {
              classAndObjectCount = classAndObjectCount + 1
            } else if (ch.getText.trim != "") {
              classAndObjectCount = classAndObjectCount + 1
            }
          }

          if (!isObject || classAndObjectCount > 1) {
            file.getChildren.foreach(child => {
              if (child.getText.trim != "" && child.getText.trim != "\n" && (!child.isInstanceOf[PsiComment] && !child.isInstanceOf[ScDocComment])) {
                val outputStream: OutputStream = processHandler.getProcessInput
                try {
                  val text = child.getText.trim//.replaceAll("\n", "")
                  offsets.add(child.getTextRange.getEndOffset)
                  val bytes: Array[Byte] = (text + "\n").getBytes
                  outputStream.write(bytes)
                  outputStream.flush()
                }
                catch {
                  case e: IOException => //ignore
                }
              }
            })
          } else if (isObject && classAndObjectCount == 1){
            myObject.getChildren.foreach(child => {
              if (child.isInstanceOf[ScExtendsBlock]) {
                child.getChildren.foreach(child => {
                  if (child.isInstanceOf[ScTemplateBody]) {
                    child.getChildren.foreach(child => {
                      if (child.getText.trim != ""  && child.getText.trim != "\n" && (!child.isInstanceOf[PsiComment] && !child.isInstanceOf[ScDocComment])) {
                        val outputStream: OutputStream = processHandler.getProcessInput
                        try {
                          val text = child.getText.trim//.replaceAll("\n", "")
                          offsets.add(child.getTextRange.getEndOffset)
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
        val worksheetView = new ConsoleViewImpl(project, false)

        WorksheetInfo.addWorksheet(project, processHandler, editor)

        evaluateWorksheet(psiFile.asInstanceOf[ScalaFile], processHandler, editor)

        val myProcessListener: ProcessAdapter = new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            printResults(event.getText, ConsoleViewContentType.getConsoleViewType(outputType), editor)
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

        val res = new DefaultExecutionResult(worksheetView, processHandler,
          createActions(worksheetView, processHandler, executor): _*)
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
    val cpVFiles = new HashSet[VirtualFile]
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