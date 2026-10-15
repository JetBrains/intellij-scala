package org.jetbrains.plugins.scala.components.libextensions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.ApiStatus

import scala.reflect.ClassTag

/**
 * The extensions that libraries contribute to the plugin (e.g. macro support, synthetic members), by interface.
 *
 * They are found in, downloaded and loaded from library jars, which is backend work, so the language layer only
 * knows this interface. Without an implementation (e.g. on a frontend) there are no library extensions.
 */
@ApiStatus.Internal
trait LibraryExtensions {
  def getExtensions[T](implicit tag: ClassTag[T]): Seq[T]
}

object LibraryExtensions {

  /** Incremented whenever the set of loaded library extensions changes */
  val ModTracker: SimpleModificationTracker = new SimpleModificationTracker

  def instance(project: Project): LibraryExtensions =
    ApplicationManager.getApplication.getService(classOf[Provider]) match {
      case null     => NoLibraryExtensions
      case provider => provider.extensions(project)
    }

  /** Gives the [[LibraryExtensions]] of a project; an application service, so that they can be a project one */
  @ApiStatus.Internal
  trait Provider {
    def extensions(project: Project): LibraryExtensions
  }

  private object NoLibraryExtensions extends LibraryExtensions {
    override def getExtensions[T](implicit tag: ClassTag[T]): Seq[T] = Seq.empty
  }
}
