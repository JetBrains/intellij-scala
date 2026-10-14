package org.jetbrains.plugins.scala.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.{ApiStatus, TestOnly}

/**
 * The [[ScalaFeatures]] that apply to a file, for lexing, parsing and highlighting.
 *
 * They come from the project model (the file's module and what the project model stores on its directory), which is
 * backend data, so the language layer only knows this interface. Without an implementation (e.g. on a frontend) there
 * are no file features, and callers fall back to the features of the file's language.
 */
@ApiStatus.Internal
trait ScalaFileFeatures {
  /** The features of the file's module, if it belongs to a Scala module */
  def moduleFeatures(file: PsiFile): Option[ScalaFeatures]

  /** The features of the file/directory; cheaper than [[moduleFeatures]] */
  def fileFeatures(file: PsiFile): Option[ScalaFeatures]

  /** Whether the file's module is compiled in the compiler's strict mode */
  def isCompilerStrictMode(file: PsiFile): Boolean
}

object ScalaFileFeatures {

  // TODO: this is a dirty hack to suppress skipping features check in unit tests
  //  ideally we shouldn't check for `isUnitTestMode`, we should fix expected test data in all affected tests
  @TestOnly
  var enableFeaturesCheckInTests = false

  def instance: ScalaFileFeatures =
    ApplicationManager.getApplication.getService(classOf[ScalaFileFeatures]) match {
      case null    => NoFileFeatures
      case service => service
    }

  private object NoFileFeatures extends ScalaFileFeatures {
    override def moduleFeatures(file: PsiFile): Option[ScalaFeatures] = None

    override def fileFeatures(file: PsiFile): Option[ScalaFeatures] = None

    override def isCompilerStrictMode(file: PsiFile): Boolean = false
  }
}
