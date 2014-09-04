package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Author: Alexander Podkhalyuzin
 * Date: 22.02.2008
 */
trait ScSimpleTypeElement extends ScTypeElement {

  def reference: Option[ScStableCodeReferenceElement] = findChild(classOf[ScStableCodeReferenceElement])
  def pathElement: ScPathElement = findChildByClassScala(classOf[ScPathElement])

  def singleton: Boolean

  def findConstructor: Option[ScConstructor]

  @volatile
  protected var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  /**
   * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
   * In case of implicit parameter with type ClassManifest[T]
   * this method will return ClassManifest with substitutor of type T.
   * @return implicit parameters used for this expression
   */
  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()
    getType(TypingContext.empty) //to update implicitParameters field
    implicitParameters
  }
}