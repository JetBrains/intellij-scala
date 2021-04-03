package org.jetbrains.plugins.scala.externalLibraries.derevo

import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.ScalaTypePresentation

import scala.util.Random

/**
  * Support for https://github.com/tofu-tf/derevo
  *
  * Based on CirceCodecInjector
  * 
  * Does not do typechecking etc
  * so users may face errors when running actual compilation.
  *
  * @author odbc
  * @since  04/04/2021
  */
class DerevoInjector extends SyntheticMembersInjector {
  private val derevoDerive: String = "derevo.derive"
  
  private val DeriveEnc: String = "_root_.derevo.circe.encoder.type"
  private val DeriveDec: String = "_root_.derevo.circe.decoder.type"

  private val Enc: String = "_root_.io.circe.Encoder"
  private val Dec: String = "_root_.io.circe.Decoder"
  private val Dummy: String = "_root_.scala.Predef.???"

  // fast check
  private def hasDerive(source: ScTypeDefinition): Boolean = source.findAnnotationNoAliases(derevoDerive) != null

  // so annotated sealed traits will generate a companion
  override def needsCompanionObject(source: ScTypeDefinition): Boolean = hasDerive(source)

  // add implicits to case object / case class companions
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case cob: ScObject if hasDerive(cob) =>
        genImplicits(cob, cob.typeParameters)
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if hasDerive(clazz) =>
            genImplicits(clazz, clazz.typeParameters)
          case _ => Nil
        }
      case _ => Nil
    }
  }

  private def genImplicits(clazz: ScTypeDefinition, typarams: Seq[ScTypeParam]): Seq[String] = {
    val args = extractDeriveArgs(clazz)

    val tparams: Seq[String] = typarams.map(tp => tp.name)
    val tps: String = join(tparams)

    val nme: String =
      if (clazz.isObject) clazz.name + ScalaTypePresentation.ObjectTypeSuffix
      else clazz.name

    args.flatMap {
      case DeriveEnc =>
        val id: Int = Random.nextInt(1000000)
        val (encParams, encCtxBounds) =
          if (clazz.typeParameters.isEmpty) ("","")
          else (s"[$tps]", s"[${join(tparams.map(p => s"$p: $Enc"))}]")
        Seq(s"implicit def enc_$id$encCtxBounds: $Enc[$nme$encParams] = $Dummy")

      case DeriveDec =>
        val id: Int = Random.nextInt(1000000)
        val (decParams, decCtxBounds) =
          if (clazz.typeParameters.isEmpty) ("","")
          else (s"[$tps]", s"[${join(tparams.map(p => s"$p: $Dec"))}]")
        Seq(s"implicit def dec_$id$decCtxBounds: $Dec[$nme$decParams] = $Dummy")

      case _ => Seq.empty
    }
  }

  private def extractDeriveArgs(clazz: ScTypeDefinition): Seq[String] = {
    val argsList = clazz.findAnnotation(derevoDerive) match {
      case annotation: ScAnnotation => annotation.constructorInvocation.arguments
      case _ => Seq.empty
    }

    argsList
      .flatMap(_.exprs)
      .flatMap(_.`type`().map(_.canonicalText).toSeq)
  }

  private def join(xs: Seq[String]): String = xs.mkString(",")
}
