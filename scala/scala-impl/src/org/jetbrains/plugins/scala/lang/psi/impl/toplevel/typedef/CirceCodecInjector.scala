package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import scala.util.Random
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation


/**
 * Support for https://github.com/circe/circe
 *
 * Chooses to skip encoder/decoder derivation (and requirements) in order to
 * provide a much faster editing experience. Users may experience
 * deriving failures when they run the real compiler.
 *
 * @author tkroman
 * @since  06/10/2018
 */
class CirceCodecInjector extends SyntheticMembersInjector {
  private val jc: String = "JsonCodec"
  private val cjc: String = "ConfiguredJsonCodec"
  private val fqnJc: String = "io.circe.generic.JsonCodec"
  private val fqnCjc: String = "io.circe.generic.extras.ConfiguredJsonCodec"

  // fast check
  private def hasCodecs(source: ScTypeDefinition): Boolean = {
    (source.findAnnotationNoAliases(jc) != null) ||
      (source.findAnnotationNoAliases(fqnJc) != null) ||
      (source.findAnnotationNoAliases(cjc) != null) ||
      (source.findAnnotationNoAliases(fqnCjc) != null)
  }

  // slower, more correct
  private def getDeriving(source: ScTypeDefinition): Option[ScAnnotation] = {
    source.annotations(jc).headOption orElse
      source.annotations(cjc).headOption orElse
      source.annotations(fqnJc).headOption orElse
      source.annotations(fqnCjc).headOption
  }

  // so annotated sealed traits will generate a companion
  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    hasCodecs(source)

  // add implicits to case object / case class companions
  override def injectFunctions(source: ScTypeDefinition): Seq[String] =
    source match {
      case cob: ScObject if hasCodecs(cob) =>
        genImplicits(cob, cob.typeParameters)
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if hasCodecs(clazz) =>
            genImplicits(clazz, clazz.typeParameters)
          case _ => Nil
        }
      case _ => Nil
    }

  private val Enc = "_root_.io.circe.Encoder"
  private val Dec = "_root_.io.circe.Decoder"
  private val Dummy = "_root_.scala.Predef.???"

  private def genImplicits(clazz: ScTypeDefinition, typarams: Seq[ScTypeParam]): Seq[String] = {
    val tparams: Seq[String] = typarams.map(tp => tp.name)

    val nme: String = if (clazz.isObject) {
      clazz.name + ScalaTypePresentation.ObjectTypeSuffix
    } else {
      clazz.name
    }

    val ((encParams, decParams), (encCtxBounds, decCtxBounds)) = if (clazz.typeParameters.isEmpty) {
      (("",""),("",""))
    } else {
      val tps: String = join(tparams)
      (
        (s"[$tps]", s"[$tps]"),
        (
          s"[${join(tparams.map(p => s"$p: $Enc"))}]",
          s"[${join(tparams.map(p => s"$p: $Dec"))}]"
        ),
      )
    }
    val id: Int = Random.nextInt(1000000)

    Seq(
      s"implicit def enc_$id$encCtxBounds: $Enc[$nme$encParams] = $Dummy",
      s"implicit def dec_$id$decCtxBounds: $Dec[$nme$decParams] = $Dummy"
    )
  }

  private def join(xs: Seq[String]): String = xs.mkString(",")
}
