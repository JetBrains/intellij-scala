package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation

/**
 * Support for https://github.com/fommil/stalactite
 *
 * Chooses to skip typeclass derivation (and requirements) in order to
 * provide a much faster editing experience. Users may experience
 * deriving failures when they run the real compiler.
 *
 * @author Sam Halliday
 * @since  24/08/2017
 */
class StalactiteInjector extends SyntheticMembersInjector {
  // fast check
  def hasStalactite(source: ScTypeDefinition): Boolean =
    source.findAnnotationNoAliases("stalactite.deriving") != null

  // slower, more correct
  def stalactite(source: ScTypeDefinition): ScAnnotation =
    source.annotations("stalactite.deriving").head

  // so annotated sealed traits will generate a companion
  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    hasStalactite(source)

  // add implicits to case object / case class companions
  override def injectFunctions(source: ScTypeDefinition): Seq[String] =
    source match {
      case cob: ScObject if hasStalactite(cob) =>
        genImplicits(cob.name + ".type", stalactite(cob))
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if hasStalactite(clazz) =>
            genImplicits(clazz.name, stalactite(clazz))
          case _ => Nil
        }
      case _ => Nil
    }

  def genImplicits(clazz: String, stalactite: ScAnnotation) = {
    stalactite
      .annotationExpr
      .getAnnotationParameters
      .map { param =>
        val typeclass = param.getText
        val gen = param.getText // would be more correct to use the FQN
        s"implicit def `$gen`: $typeclass[${clazz}] = _root_.scala.Predef.???"
      }
  }
}
