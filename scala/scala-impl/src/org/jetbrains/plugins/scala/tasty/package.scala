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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project._

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
      element.containingFile.flatMap { psiFile =>
        element.module.flatMap { module =>
          val index = ProjectFileIndex.SERVICE.getInstance(element.getProject)
          val inTest = index.isInTestSourceContent(psiFile.getVirtualFile)
          Option(CompilerPaths.getModuleOutputPath(module, inTest)).flatMap { outputDirectory =>
            element.parentsInFile.filterByType[ScTypeDefinition].lastOption.map { topLevelTypeDefinition =>
              TastyPath(outputDirectory, topLevelTypeDefinition.qualifiedName)
            }
          }
        }
      }
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
