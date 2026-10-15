package org.jetbrains.plugins.scala.lang.psi.stubs.util

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCaptureTypeElement, ScDesugarizableTypeElement, ScInfixTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

/** The names of the direct supertypes of a template, which its extends block stub stores */
object ScalaDirectSupers {
  private val defaultParents   : Array[String] = Array("AnyRef")
  private val caseClassDefaults: Array[String] = defaultParents :+ "Product" :+ "Serializable"

  def directSupersNames(extBlock: ScExtendsBlock): ArraySeq[String] = {
    def default = if (extBlock.isUnderCaseClass) caseClassDefaults else defaultParents

    collectForDirectSuperReferences(extBlock, _.refName) ++ default
  }

  private def collectForDirectSuperReferences(extBlock: ScExtendsBlock,
                                              function: ScStableCodeReference => String): ArraySeq[String] = {
    @tailrec
    def extractReference(te: ScTypeElement): Option[ScStableCodeReference] =
      te match {
        case simpleType: ScSimpleTypeElement => simpleType.reference
        case infixType: ScInfixTypeElement => Option(infixType.operation)
        case x: ScParameterizedTypeElement => extractReference(x.typeElement)
        case x: ScParenthesisedTypeElement =>
          x.innerElement match {
            case Some(e) => extractReference(e)
            case _ => None
          }
        case x: ScCaptureTypeElement => extractReference(x.innerElement)
        case x: ScDesugarizableTypeElement => extractReference(x.typeElementFromText(x.desugarizedText))
        case _ => None
      }

    extBlock.templateParents match {
      case None => ArraySeq.empty
      case Some(parents) =>
        val parentElements = parents.typeElements.iterator
        val builder = ArraySeq.newBuilder[String]
        while (parentElements.hasNext) {
          extractReference(parentElements.next()) match {
            case Some(value) =>
              builder += function(value)
            case _ =>
          }
        }
        builder.result()
    }
  }
}
