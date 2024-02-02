package org.jetbrains.plugins.scala
package debugger

import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.breakpoints.{Breakpoint, BreakpointManager, JavaLineBreakpointType}
import com.intellij.debugger.{BreakpointComment, DebuggerInvocationUtil, DebuggerTestCase}
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.application.ModalityState
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.{XDebuggerManager, XDebuggerUtil}
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.scala.compiler.ScalaExecutionTestCase
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.project._
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

import java.io._
import javax.swing.SwingUtilities
import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

/**
 * ATTENTION: when updating any paths which might be cached between builds
 * ensure to update org.jetbrains.scalateamcity.common.Caching fields
 */
@Category(Array(classOf[DebuggerTests]))
abstract class ScalaDebuggerTestCase extends DebuggerTestCase with ScalaExecutionTestCase {

  import ScalaDebuggerTestCase.ScalaLineBreakpointTypeClassName

  private val javaClasses: mutable.Set[String] = mutable.Set.empty

  override protected def testDataDirectoryName: String = "debugger"

  override protected def initApplication(): Unit = {
    super.initApplication()
    NodeRendererSettings.getInstance().getClassRenderer.SHOW_DECLARED_TYPE = false
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
    if (javaClasses(className)) {
      super.createBreakpoints(className)
      return
    }

    val classFilePath = s"${className.split('.').mkString(File.separator)}.class"
    if (!classFilesOutputPath.resolve(classFilePath).toFile.exists()) {
      org.junit.Assert.fail(s"Could not find compiled class $className")
    }

    val manager = ScalaPsiManager.instance(getProject)
    val psiClass = inReadAction(manager.getCachedClass(GlobalSearchScope.allScope(getProject), className))
    val psiFile = psiClass.map(_.getContainingFile).getOrElse(throw new AssertionError(s"Could not find class $className"))

    val runnable: Runnable = () => {
      val breakpointManager = XDebuggerManager.getInstance(getProject).getBreakpointManager
      val bpType = XDebuggerUtil.getInstance()
        .findBreakpointType(Class.forName(ScalaLineBreakpointTypeClassName).asInstanceOf[Class[JavaLineBreakpointType]])
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
            val commentText = text.substring(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber))
            val comment = BreakpointComment.parse(commentText, virtualFile.getName, lineNumber)
            val lambdaOrdinal: Integer =
              comment.readValue(lambdaOrdinalString).pipe { ordinalStr =>
                if (ordinalStr == null) null
                else ordinalStr.toInt
              }
            props.setEncodedInlinePosition(lambdaOrdinal)
            val xbp = inWriteAction(breakpointManager.addLineBreakpoint(bpType, virtualFile.getUrl, lineNumber, props))
            val javaBp = BreakpointManager.getJavaBreakpoint(xbp)
              .asInstanceOf[Breakpoint[_ <: JavaLineBreakpointProperties]]
            BreakpointManager.addBreakpoint(javaBp)
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

  protected def addJavaSourceFile(path: String, className: String, contents: String): Unit = {
    addSourceFile(path, contents)
    javaClasses += className
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

      val bpType = XDebuggerUtil.getInstance()
        .findBreakpointType(Class.forName(ScalaLineBreakpointTypeClassName).asInstanceOf[Class[JavaLineBreakpointType]])
      val breakpointManager = XDebuggerManager.getInstance(getProject).getBreakpointManager

      if (bpType.canPutAt(vFile, lineNumber, getProject)) {
        val props = bpType.createBreakpointProperties(vFile, lineNumber)
        props.setEncodedInlinePosition(null)
        val xbp = inWriteAction(breakpointManager.addLineBreakpoint(bpType, vFile.getUrl, lineNumber, props))
        val javaBp = BreakpointManager.getJavaBreakpoint(xbp)
          .asInstanceOf[Breakpoint[_ <: JavaLineBreakpointProperties]]
        BreakpointManager.addBreakpoint(javaBp)
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

  protected def lambdaOrdinal(n: Int): String = s"$lambdaOrdinalString($n)"
}

private object ScalaDebuggerTestCase {
  final val ScalaLineBreakpointTypeClassName = "org.jetbrains.plugins.scala.debugger.breakpoints.ScalaLineBreakpointType"
}
