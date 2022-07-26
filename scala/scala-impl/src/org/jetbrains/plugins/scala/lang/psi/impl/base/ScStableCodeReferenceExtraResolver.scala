package org.jetbrains.plugins.scala.lang.psi.impl.base
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi._
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.IteratorExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._

import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * This helper class was primarily created to support resolving of ammonite specific references (like import from $file or $ivy).
 * The interface was created to extract ammonite classes to a separate module.
 *
 * Used in [[ScStableCodeReferenceImpl]].
 */
@ApiStatus.Internal
trait ScStableCodeReferenceExtraResolver {

  def acceptsFile(file: ScalaFile): Boolean

  def resolve(ref: ScStableCodeReference): Option[PsiNamedElement]

  final def resolveWithFileCheck(ref: ScStableCodeReference): Option[PsiNamedElement] =
    ref.getContainingFile match {
      case file: ScalaFile if acceptsFile(file) => resolve(ref)
      case _                                    => None
    }
}

object ScStableCodeReferenceExtraResolver {
  private val CLASS_NAME = "org.intellij.scala.referenceExtraResolver"

  private val EP_NAME: ExtensionPointName[ScStableCodeReferenceExtraResolver] =
    ExtensionPointName.create(CLASS_NAME)

  def resolveWithFileCheck(ref: ScStableCodeReference): Option[PsiNamedElement] =
    EP_NAME.getExtensionList.stream().iterator().asScala.map(_.resolveWithFileCheck(ref)).headOption.flatten
}