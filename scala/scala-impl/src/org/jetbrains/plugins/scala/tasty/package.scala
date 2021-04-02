package org.jetbrains.plugins.scala

import java.util.concurrent.TimeUnit
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.AlarmFactory
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.externalHighlighters.compiler.DocumentCompiler
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project._
import com.intellij.openapi.module.Module

import java.io.File

package object tasty {
  //noinspection ScalaExtractStringToBundle
  @Nls
  val tastyName = "TASTy"

  def isTastyEnabledFor(element: PsiElement): Boolean =
    ScalaHighlightingMode.showDotcErrors && element.isInScala3Module
//    element.getLanguage.is(Scala3Language.INSTANCE) // TODO SCL-17237

  case class TastyPath(classpath: String, className: String)

  object TastyPath {
    private val JarPath = """(.+.jar)!/(.+)\.tasty""".r
    private val CompileOutputPath = """(.+/(?:classes|(?:out/(?:production|test))))/(.+)\.tasty""".r

    def apply(tastyFile: VirtualFile): Option[TastyPath] = Some(tastyFile.getPath) collect {
      case JarPath(outputDirectory, relativePath) =>
        TastyPath(outputDirectory, relativePath.replace('/', '.'))

      case CompileOutputPath(outputDirectory, relativePath) =>
        TastyPath(outputDirectory, relativePath.replace('/', '.'))
    }

    def apply(element: PsiElement): Option[TastyPath] = {
      def getDocumentCompilerOutputDir(module: Module, className: String): Option[String] =
        for {
          outputDirectory <- DocumentCompiler.outputDirectoryFor(module)
          tastyFile = outputDirectory.toPath.resolve(className.replace('.', File.separatorChar) + ".tasty").toFile
          if tastyFile.exists()
        } yield outputDirectory.getAbsolutePath

      def getModuleOutputDir(module: Module): Option[String] =
        for {
          psiFile <- element.containingFile
          virtualFile <- Option(psiFile.getVirtualFile)
          inTest = ProjectFileIndex.SERVICE.getInstance(element.getProject).isInTestSourceContent(virtualFile)
          outputDirectory <- Option(CompilerPaths.getModuleOutputPath(module, inTest))
        } yield outputDirectory

      for {
        module <- element.module
        topLevelTypeDefinition <- element.parentsInFile.filterByType[ScTypeDefinition].lastOption
        className = topLevelTypeDefinition.qualifiedName
        outputDirectory <- getDocumentCompilerOutputDir(module, className).orElse(getModuleOutputDir(module))
      } yield TastyPath(outputDirectory, className)
    }
  }

  def typeAt(offset: Int, tastyFile: TastyFile): Option[String] =
    tastyFile.types
      .find(it => it.position.start <= offset && offset <= it.position.end)
      .map(_.presentation)

  def referenceTargetAt(offset: Int, tastyFile: TastyFile): Option[(String, Int)] =
    tastyFile.references
      .find(it => it.position.start <= offset && offset <= it.position.end)
      .map(_.target)
      .map(position => (position.file, position.start))

  def showTastyNotification(@Nls message: String): Unit = if (ApplicationManager.getApplication.isInternal) {
    invokeLater {
      val notification = new Notification(tastyName, tastyName, message, NotificationType.INFORMATION)
      Notifications.Bus.notify(notification)
      AlarmFactory.getInstance.create.addRequest((() => notification.expire()): Runnable, TimeUnit.SECONDS.toMillis(2))
    }
  }
}
