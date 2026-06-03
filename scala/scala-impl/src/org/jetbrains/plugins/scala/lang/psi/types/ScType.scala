package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.NameRenderer
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation.PresentationOptions
import org.jetbrains.plugins.scala.project.ProjectContextOwner
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.language.implicitConversions

trait ScType extends ProjectContextOwner {

  def typeSystem: api.TypeSystem = projectContext.typeSystem

  private var cachedAliasType = new ContextDependent[Option[AliasType]]()

  final def aliasType(implicit context: Context): Option[AliasType] = cachedAliasType.get match {
    case Some(value) => value
    case None =>
      ProgressManager.checkCanceled()
      val (value, valueInContext) = cachedAliasType.updatedUsing(ctx => calculateAliasType(ctx))
      cachedAliasType = valueInContext
      value
  }

  final def isAliasType(implicit context: Context): Boolean = aliasType.isDefined

  private var unpacked: ScType = _

  final def unpackedType: ScType = {
    if (unpacked == null) {
      ProgressManager.checkCanceled()
      unpacked = unpackedTypeInner
    }
    unpacked
  }

  protected def calculateAliasType(implicit context: Context): Option[AliasType] = None

  // TODO: we must not override toString which does such a complex stuff (resolve, tree traversal etc...)
  //  for such things we should always use explicit methods oText/mkString/presentableText/etc...
  override final def toString: String = ifReadAllowed {
    presentableText(TypePresentationContext.emptyContext, Context.Empty)
  }(getClass.getSimpleName)

  def isValue: Boolean

  def isFinalType(implicit context: Context): Boolean = false

  def inferValueType: api.ValueType

  protected def unpackedTypeInner: ScType = ScExistentialType(this) match {
    case ScExistentialType(q, Seq())                                       => q
    case ScExistentialType(arg: ScExistentialArgument, Seq(w)) if w == arg => arg.upper
    case ex                                                                => ex
  }

  def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean)(implicit context: Context): ConstraintsResult = {
    ConstraintsResult.Left
  }

  def visitType(visitor: ScalaTypeVisitor): Unit

  def typeDepth: Int = 1

  @Nls
  def presentableText(implicit tpc: TypePresentationContext, context: Context): String =
    typeSystem.presentableText(this)

  def canonicalText: String = canonicalText(TypePresentationContext.emptyContext)(Context.Empty)

  def canonicalText(tpc: TypePresentationContext)(implicit context: Context): String = typeSystem.canonicalText(this, tpc)

  def typeText(nameRenderer: NameRenderer, options: PresentationOptions)(implicit tpc: TypePresentationContext, context: Context): String =
    typeSystem.typeText(this, nameRenderer, options)
}

object ScType {
  implicit def recursiveExtensions(tp: ScType): recursiveUpdate.Extensions = new recursiveUpdate.Extensions(tp)
}

trait NamedType extends ScType {
  val name: String

  private def renderedName: String =
    TypeParameterDebugRendering.renderNamedTypeName(this)

  override def presentableText(implicit tpc: TypePresentationContext, context: Context): String = renderedName

  override def canonicalText: String =
    if (ScalaApplicationSettings.PRECISE_TEXT) super.canonicalText // #SCL-21178
    else renderedName
}
