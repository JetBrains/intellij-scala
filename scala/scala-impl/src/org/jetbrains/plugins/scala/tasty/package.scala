package org.jetbrains.plugins.scala

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.AlarmFactory
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project._

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

package object tasty {
  // TODO Remove the structural types when the project use Scala 2.13
  import scala.language.reflectiveCalls

  type TastyFile = {
    def text: String
    def references: Array[ReferenceData]
    def types: Array[TypeData]
  }
  type Position = {
    def file: String
    def startLine: Int
    def endLine: Int
    def startColumn: Int
    def endColumn: Int
  }
  type ReferenceData = {
    def position: Position
    def target: Position
  }
  type TypeData = {
    def position: Position
    def presentation: String
  }
  private[tasty] type TastyReader = {
    def read(classpath: String, className: String): TastyFile
  }

  def isTastyEnabledFor(element: PsiElement): Boolean =
    element.getContainingFile.getLanguage.is(Scala3Language.INSTANCE)

  case class Location(outputDirectory: String, className: String)

  def compiledLocationOf(sourceFile: PsiFile): Option[Location] = {
    val virtualFile = sourceFile.getVirtualFile
    val index = ProjectFileIndex.SERVICE.getInstance(sourceFile.getProject)
    val inTest = index.isInTestSourceContent(virtualFile)

    for {
      module <- sourceFile.module
      outputDirectory <- Option(CompilerPaths.getModuleOutputPath(module, inTest))
      sourceRoot <- Option(index.getSourceRootForFile(virtualFile))
      relativeSourcePath = virtualFile.getPath.substring(sourceRoot.getPath.length + 1)
      className = relativeSourcePath.dropRight(6).replace('/', '.') // TODO Handle class <-> file naming conventions
    } yield Location(outputDirectory, className)
  }

  private val OutputPath = """(.+[\\\/](?:classes|(?:out[\\\/](?:production|test))))[\\\/](.+\.tasty)""".r

  def locationOf(compiledFile: VirtualFile): Option[Location] = compiledFile.getPath match {
    case OutputPath(outputDirectory, relativePath) =>
      val className = relativePath.dropRight(6).replaceAll("""[\\/]""", ".")
      Some(Location(outputDirectory, className))
    case _ => None
  }

  def typeAt(position: LogicalPosition, tastyFile: TastyFile): Option[String] = {
    tastyFile.types.find(it => it.position.startLine <= position.line && position.line <= it.position.endLine &&
      it.position.startColumn <= position.column && position.column <= it.position.endColumn).map(_.presentation)
  }

  def referenceTargetAt(position: LogicalPosition, tastyFile: TastyFile): Option[(File, Int)] = {
    val reference = tastyFile.references.find(it => it.position.startLine <= position.line && position.line <= it.position.endLine &&
      it.position.startColumn <= position.column && position.column <= it.position.endColumn).map(_.target)

    reference.map(position => (new File(position.file), offsetOf(position)))
  }

  private def offsetOf(position: Position): Int = {
    val lines = Files.readAllLines(Paths.get(position.file)).asScala
    lines.take(position.startLine).map(_.length + 1).sum + position.startColumn
  }

  def showTastyNotification(message: String): Unit = if (ApplicationManager.getApplication.isInternal) {
    invokeLater {
      val notification = new Notification("TASTy", "TASTy", message, NotificationType.INFORMATION)
      Notifications.Bus.notify(notification)
      AlarmFactory.getInstance.create.addRequest(() => notification.expire(), TimeUnit.SECONDS.toMillis(1))
    }
  }
}
