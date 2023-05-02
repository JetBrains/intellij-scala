package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.project.ProjectContextOwner
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.language.implicitConversions

trait ScType extends ProjectContextOwner {

  def typeSystem: api.TypeSystem = projectContext.typeSystem

  private var aliasTypeInner: Option[AliasType] = _

  final def aliasType: Option[AliasType] = {
    if (aliasTypeInner == null) {
      ProgressManager.checkCanceled()
      aliasTypeInner = calculateAliasType
    }
    aliasTypeInner
  }

  final def isAliasType: Boolean = aliasType.isDefined

  private var unpacked: ScType = _

  final def unpackedType: ScType = {
    if (unpacked == null) {
      ProgressManager.checkCanceled()
      unpacked = unpackedTypeInner
    }
    unpacked
  }

  protected def calculateAliasType: Option[AliasType] = None

  // TODO: we must not override toString which does such a complex stuff (resolve, tree traversal etc...)
  //  for such things we should always use explicit methods oText/mkString/presentableText/etc...
  override final def toString: String = ifReadAllowed {
    presentableText(TypePresentationContext.emptyContext)
  }(getClass.getSimpleName)

  def isValue: Boolean

  def isFinalType: Boolean = false

  def inferValueType: api.ValueType

  protected def unpackedTypeInner: ScType = ScExistentialType(this) match {
    case ScExistentialType(q, Seq())                                       => q
    case ScExistentialType(arg: ScExistentialArgument, Seq(w)) if w == arg => arg.upper
    case ex                                                                => ex
  }

  def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    ConstraintsResult.Left
  }

  def visitType(visitor: ScalaTypeVisitor): Unit

  def typeDepth: Int = 1

  def presentableText(implicit context: TypePresentationContext): String =
    typeSystem.presentableText(this)

  def urlText(implicit context: TypePresentationContext): String =
    typeSystem.urlText(this)

  def canonicalText: String = typeSystem.canonicalText(this)
}

object ScType {
  implicit def recursiveExtensions(tp: ScType): recursiveUpdate.Extensions = new recursiveUpdate.Extensions(tp)
}

trait NamedType extends ScType {
  val name: String

  override def presentableText(implicit context: TypePresentationContext): String = name

  override def canonicalText: String =
    if (ScalaApplicationSettings.PRECISE_TEXT) super.canonicalText // #SCL-21178
    else name
}
