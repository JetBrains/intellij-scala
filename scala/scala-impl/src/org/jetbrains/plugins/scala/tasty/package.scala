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

  private[tasty] type TastyReader = { def read(classpath: String, className: String): TastyFile }
  type TastyFile = { def text: String; def references: Array[ReferenceData]; def types: Array[TypeData] }
  type Position = { def file: String; def startLine: Int; def endLine: Int; def startColumn: Int; def endColumn: Int }
  type ReferenceData = { def from: Position; def to: Position }
  type TypeData = { def from: Position; def presentation: String }

  def isTastyEnabledFor(element: PsiElement): Boolean =
    element.getContainingFile.getLanguage.is(Scala3Language.INSTANCE)

  case class Location(outputDirectory: String, className: String)

  def compiledLocationOf(sourceFile: PsiFile): Option[Location] = {
    val index = ProjectFileIndex.SERVICE.getInstance(sourceFile.getProject)
    val inTest = index.isInTestSourceContent(sourceFile.getVirtualFile)

    sourceFile.module.flatMap { module =>
      Option(CompilerPaths.getModuleOutputPath(module, inTest)).flatMap { outputDirectory =>
        Option(index.getSourceRootForFile(sourceFile.getVirtualFile)).flatMap { sourceRoot =>
          val relativeSourcePath = sourceFile.getVirtualFile.getPath.substring(sourceRoot.getPath.length + 1)
          // TODO Perform the check outside this method
          val tastyFile = new File(outputDirectory, relativeSourcePath.dropRight(6) + ".tasty")
          if (tastyFile.exists) {
            // TODO Handle class <-> file naming conventions
            val className = relativeSourcePath.dropRight(6).replace('/', '.')
            Some(Location(outputDirectory, className))
          } else {
            None
          }
        }
      }
    }
  }

  private val OutputPath = """(.+[\\\/](?:classes|(?:out[\\\/](?:production|test))))[\\\/](.+\.tasty)""".r

  def locationOf(compiledFile: VirtualFile): Option[Location] = compiledFile.getPath match {
    case OutputPath(outputDirectory, relativePath) =>
      val className = relativePath.dropRight(6).replaceAll("""[\\/]""", ".")
      Some(Location(outputDirectory, className))
    case _ => None
  }

  def typeAt(position: LogicalPosition, tastyFile: TastyFile): Option[String] = {
    tastyFile.types.find(it => it.from.startLine <= position.line && position.line <= it.from.endLine &&
      it.from.startColumn <= position.column && position.column <= it.from.endColumn).map(_.presentation)
  }

  def referenceTargetAt(position: LogicalPosition, tastyFile: TastyFile): Option[(File, Int)] = {
    val reference = tastyFile.references.find(it => it.from.startLine <= position.line && position.line <= it.from.endLine &&
      it.from.startColumn <= position.column && position.column <= it.from.endColumn).map(_.to)

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
