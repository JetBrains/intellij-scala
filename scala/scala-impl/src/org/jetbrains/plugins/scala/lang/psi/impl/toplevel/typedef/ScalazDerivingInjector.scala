package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation

/**
 * Support for https://gitlab.com/fommil/scalaz-deriving
 *
 * Chooses to skip typeclass derivation (and requirements) in order to
 * provide a much faster editing experience. Users may experience
 * deriving failures when they run the real compiler.
 *
 * @author Sam Halliday
 * @since  24/08/2017
 */
class ScalazDerivingInjector extends SyntheticMembersInjector {
  // fast check
  private def hasDeriving(source: ScTypeDefinition): Boolean =
    (source.findAnnotationNoAliases("deriving") != null) ||
    (source.findAnnotationNoAliases("scalaz.deriving") != null) ||
    (source.findAnnotationNoAliases("xderiving") != null) ||
    (source.findAnnotationNoAliases("scalaz.xderiving") != null) ||
    (source.findAnnotationNoAliases("stalactite.deriving") != null)

  // slower, more correct
  private def getDeriving(source: ScTypeDefinition): Option[ScAnnotation] =
    source.annotations("deriving").headOption orElse
    source.annotations("scalaz.deriving").headOption orElse
    source.annotations("xderiving").headOption orElse
    source.annotations("scalaz.xderiving").headOption orElse
    source.annotations("stalactite.deriving").headOption

  // so annotated sealed traits will generate a companion
  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    hasDeriving(source)

  // add implicits to case object / case class companions
  override def injectFunctions(source: ScTypeDefinition): Seq[String] =
    source match {
      case cob: ScObject if hasDeriving(cob) =>
        genImplicits(cob.name + ScalaTypePresentation.ObjectTypeSuffix, getDeriving(cob))
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if hasDeriving(clazz) =>
            genImplicits(clazz.name, getDeriving(clazz))
          case _ => Nil
        }
      case _ => Nil
    }

  private def genImplicits(clazz: String, ann: Option[ScAnnotation]): Seq[String] = {
    for {
      derivingAnn <- ann.toSeq
      param <- derivingAnn.annotationExpr.getAnnotationParameters
      typeClassFqn <- fqn(param)
    } yield {
      s"implicit def `$typeClassFqn`: $typeClassFqn[$clazz] = _root_.scala.Predef.???"
    }
  }

  private def fqn(annotParam: ScExpression): Option[String] = {
    annotParam match {
      case ResolvesTo(c: PsiClass) => Option(c.qualifiedName)
      case _ => None
    }
  }
}
