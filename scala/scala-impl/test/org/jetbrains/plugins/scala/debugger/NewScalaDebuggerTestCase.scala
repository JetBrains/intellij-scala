package org.jetbrains.plugins.scala
package debugger

import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.debugger.{DebuggerInvocationUtil, DebuggerTestCase}
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.{EdtTestUtil, VfsTestUtil}
import com.intellij.xdebugger.{XDebuggerManager, XDebuggerUtil}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.RevertableChange
import org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.assertTrue

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import javax.swing.SwingUtilities
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

abstract class NewScalaDebuggerTestCase extends DebuggerTestCase with ScalaSdkOwner {

  import NewScalaDebuggerTestCase.SourceFile

  private val compilerConfig: RevertableChange = CompilerTestUtil.withEnabledCompileServer(false)

  private val testDataDebuggerPath: Path = Path.of(TestUtils.getTestDataPath, "debugger")

  private lazy val versionSpecific: Path = Path.of(s"scala-${version.major}")

  private lazy val testAppPath: Path = testDataDebuggerPath.resolve(getClass.getSimpleName).resolve(versionSpecific)

  private lazy val appOutputPath: Path = Path.of(s"${testAppPath}_out")

  private lazy val srcPath: Path = testAppPath.resolve("src")

  private lazy val classFilesOutputPath: Path = appOutputPath.resolve("classes")

  private lazy val checksumsPath: Path = appOutputPath.resolve("checksums")

  private lazy val checksumsFilePath: Path = checksumsPath.resolve("checksums.dat")

  private val sourceFiles: mutable.Set[SourceFile] = mutable.Set.empty

  override protected def initOutputChecker(): OutputChecker =
    new OutputChecker(() => getTestAppPath, () => getAppOutputPath) {
      override def checkValid(jdk: Sdk, sortClassPath: Boolean): Unit = {}
    }

  override protected def getTestAppPath: String = testAppPath.toString

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true),
    HeavyJDKLoader(testProjectJdkVersion),
    SourcesLoader(srcPath.toString)
  )

  override protected def getModuleOutputDir: Path = classFilesOutputPath

  override protected def getAppOutputPath: String = getModuleOutputDir.toString

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_11

  override protected def getProjectLanguageLevel: LanguageLevel = testProjectJdkVersion

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK(testProjectJdkVersion)

  override protected def initApplication(): Unit = {
    super.initApplication()
    NodeRendererSettings.getInstance().getClassRenderer.SHOW_DECLARED_TYPE = false
  }

  override protected def setUpModule(): Unit = {
    super.setUpModule()
    EdtTestUtil.runInEdtAndWait { () =>
      setUpLibraries(getModule)
      compilerConfig.applyChange()
    }
  }

  override protected def setUp(): Unit = {
    Files.createDirectories(srcPath)
    Files.createDirectories(classFilesOutputPath)
    Files.createDirectories(checksumsPath)

    sourceFiles.foreach { srcFile =>
      val path = srcPath.resolve(srcFile.path)
      if (!path.toFile.exists() || Files.readString(path) != srcFile.contents) {
        Files.createDirectories(path.getParent)
        val bytes = srcFile.contents.getBytes(StandardCharsets.UTF_8)
        Files.write(path, bytes)
      }
    }

    super.setUp()
    LocalFileSystem.getInstance().refreshIoFiles(srcPath.toFile.listFiles().toList.asJava)
    compileProject()
  }

  override protected def tearDown(): Unit = {
    try {
      EdtTestUtil.runInEdtAndWait { () =>
        compilerConfig.revertChange()
        disposeLibraries(getModule)
      }
    } finally {
      super.tearDown()
    }
  }

  override protected def compileProject(): Unit = {
    def loadChecksumsFromDisk(): Map[Path, Array[Byte]] =
      Using(new ObjectInputStream(new FileInputStream(checksumsFilePath.toFile)))(_.readObject())
        .map(_.asInstanceOf[Map[String, Array[Byte]]])
        .map(_.map { case (path, checksum) => (Path.of(path), checksum) })
        .getOrElse(Map.empty)

    val messageDigest = MessageDigest.getInstance("MD5")

    def calculateSrcCheksums(): Map[Path, Array[Byte]] = {
      def checksum(file: File): Array[Byte] = {
        val fileBytes = Files.readAllBytes(file.toPath)
        messageDigest.digest(fileBytes)
      }

      def checksumsInDir(dir: File): List[(Path, Array[Byte])] =
        dir.listFiles().toList.flatMap { f =>
          if (f.isDirectory) checksumsInDir(f) else List((f.toPath, checksum(f)))
        }

      checksumsInDir(srcPath.toFile).toMap
    }

    def shouldCompile(srcChecksums: Map[Path, Array[Byte]], diskChecksums: Map[Path, Array[Byte]]): Boolean =
      !srcChecksums.forall { case (srcPath, srcSum) =>
        diskChecksums.get(srcPath).exists(java.util.Arrays.equals(srcSum, _))
      }

    def writeChecksumsToDisk(checksums: Map[Path, Array[Byte]]): Unit = {
      val strings = checksums.map { case (path, sum) => (path.toString, sum) }
      Using(new ObjectOutputStream(new FileOutputStream(checksumsFilePath.toFile)))(_.writeObject(strings))
    }

    val srcChecksums = calculateSrcCheksums()

    val compareChecksums = for {
      diskChecksums <- Try(loadChecksumsFromDisk())
    } yield shouldCompile(srcChecksums, diskChecksums)

    val needsCompilation = compareChecksums.getOrElse(true)

    if (needsCompilation) {
      super.compileProject()
      writeChecksumsToDisk(srcChecksums)
    }
  }

  override protected def createJavaParameters(mainClass: String): JavaParameters = {
    val params = new JavaParameters()
    params.getClassPath.addAllFiles(getModule.scalaCompilerClasspath.toArray)
    params.getClassPath.add(getAppOutputPath)
    params.setJdk(getTestProjectJdk)
    params.setWorkingDirectory(getTestAppPath)
    params.setMainClass(mainClass)
    params
  }

  override protected def createBreakpoints(className: String): Unit = {
    val classFilePath = s"${className.split('.').mkString(File.separator)}.class"
    if (!classFilesOutputPath.resolve(classFilePath).toFile.exists()) {
      org.junit.Assert.fail(s"Could not find compiled class $className")
    }

    val manager = ScalaPsiManager.instance(getProject)
    val psiClass = inReadAction(manager.getCachedClass(GlobalSearchScope.allScope(getProject), className))
    val psiFile = psiClass.map(_.getContainingFile).getOrElse(throw new AssertionError(s"Could not find class $className"))

    val runnable: Runnable = () => {
      val breakpointManager = XDebuggerManager.getInstance(getProject).getBreakpointManager
      val bpType = XDebuggerUtil.getInstance().findBreakpointType(classOf[ScalaLineBreakpointType])
      val document = PsiDocumentManager.getInstance(getProject).getDocument(psiFile)
      val text = document.getText

      var offset = -1
      var cont = true

      while (cont) {
        offset = text.indexOf(breakpoint, offset + 1)
        if (offset == -1) {
          cont = false
        } else {
          val lineNumber = document.getLineNumber(offset)
          val virtualFile = psiFile.getVirtualFile
          if (bpType.canPutAt(virtualFile, lineNumber, getProject)) {
            val props = bpType.createBreakpointProperties(virtualFile, lineNumber)
            val comment = text.substring(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber))
            val lambdaOrdinal =
              if (comment.contains(lambdaOrdinalString)) readValue(comment, lambdaOrdinalString).toInt
              else -1
            props.setLambdaOrdinal(lambdaOrdinal)
            val xbp = inWriteAction(breakpointManager.addLineBreakpoint(bpType, virtualFile.getUrl, lineNumber, props))
            BreakpointManager.addBreakpoint(BreakpointManager.getJavaBreakpoint(xbp))
          }
        }
      }
    }

    if (!SwingUtilities.isEventDispatchThread) {
      DebuggerInvocationUtil.invokeAndWait(getProject, runnable, ModalityState.defaultModalityState())
    } else {
      runnable.run()
    }
  }

  protected def addSourceFile(path: String, contents: String): Unit = {
    sourceFiles += SourceFile(path, contents)
  }

  protected def addBreakpointInLibrary(className: String, methodName: String): Unit = {
    val manager = ScalaPsiManager.instance(getProject)
    val method = inReadAction {
      val psiClass = manager.getCachedClass(GlobalSearchScope.allScope(getProject), className)
      psiClass.map(_.getNavigationElement.asInstanceOf[ScTypeDefinition]).flatMap(_.functions.find(_.name == methodName))
    }

    assertTrue(s"Method $methodName of $className not found", method.isDefined)

    val runnable: Runnable = () => {
      val file = method.get.getContainingFile
      val document = PsiDocumentManager.getInstance(getProject).getDocument(file)
      val vFile = file.getVirtualFile
      val methodDefLine = method.get.nameId.getTextRange.getStartOffset
      val methodLine = document.getLineNumber(methodDefLine)
      val lineNumber = methodLine + 1

      val bpType = XDebuggerUtil.getInstance().findBreakpointType(classOf[ScalaLineBreakpointType])
      val breakpointManager = XDebuggerManager.getInstance(getProject).getBreakpointManager

      if (bpType.canPutAt(vFile, lineNumber, getProject)) {
        val props = bpType.createBreakpointProperties(vFile, lineNumber)
        props.setLambdaOrdinal(-1)
        val xbp = inWriteAction(breakpointManager.addLineBreakpoint(bpType, vFile.getUrl, lineNumber, props))
        BreakpointManager.addBreakpoint(BreakpointManager.getJavaBreakpoint(xbp))
      }
    }

    if (!SwingUtilities.isEventDispatchThread) {
      DebuggerInvocationUtil.invokeAndWait(getProject, runnable, ModalityState.defaultModalityState())
    } else {
      runnable.run()
    }
  }

  protected val breakpoint: String = "// Breakpoint!"

  private val lambdaOrdinalString: String = "LambdaOrdinal"

  protected val lambdaOrdinal: String = s"$lambdaOrdinalString(0)"

  protected def assertEquals[A, B](expected: A, actual: B)(implicit ev: A <:< B): Unit = {
    org.junit.Assert.assertEquals(expected, actual)
  }
}

private object NewScalaDebuggerTestCase {
  private final case class SourceFile(path: String, contents: String)
}
