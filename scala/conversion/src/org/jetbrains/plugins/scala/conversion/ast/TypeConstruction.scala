package org.jetbrains.plugins.scala
package conversion
package ast

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
case class TypeConstruction(inType: String) extends IntermediateNode with TypedElement {
  def getDefaultTypeValue: String = {
    inType match {
      case "Int" | "Byte" | "Short" | "Char" => "0"
      case "Double" | "Float" => ".0"
      case "Boolean" => "false"
      case "Long" => "0L"
      case "Unit" => "{}"
      case _ => "null"
    }
  }

  override def getType: TypeConstruction = this.asInstanceOf[TypeConstruction]
}

object TypeConstruction {
  def createIntermediateTypePresentation(inType: PsiType, inProject: Project)(implicit textMode: Boolean = false): IntermediateNode = {
    implicit val ctx: ProjectContext = inProject

    val buffer = mutable.ArrayBuffer.empty[(IntermediateNode, Option[String])]
    val result = getParts(inType.toScType(paramTopLevel = true), buffer)

    result match {
      case parametrized: ParametrizedConstruction =>
        parametrized.associationMap = buffer.toSeq
      case array: ArrayConstruction =>
        array.associationMap = buffer.toSeq
      case _ =>
    }

    result
  }

  // get simple parts of type if type is array or parametrized
  def getParts(scType: ScType, buffer: mutable.ArrayBuffer[(IntermediateNode, Option[String])])
              (implicit ctx: ProjectContext,
               textMode: Boolean = false): IntermediateNode = {
    implicit val tpc: TypePresentationContext = TypePresentationContext.emptyContext
    scType match {
      case p@ParameterizedType(des, args) =>
        val typeConstruction: IntermediateNode = TypeConstruction(ctx.typeSystem.presentableText(des, withPrefix = textMode))
        buffer += ((typeConstruction, p.extractClass.flatMap(el => Option(el.getQualifiedName))))
        val argsOnLevel = args.map(getParts(_, buffer))
        ParametrizedConstruction(typeConstruction, argsOnLevel.toSeq)
      case JavaArrayType(argument) => ArrayConstruction(getParts(argument, buffer))
      case otherType =>
        val typeConstruction: IntermediateNode = TypeConstruction(ctx.typeSystem.presentableText(otherType, withPrefix = textMode))
        buffer += ((typeConstruction, otherType.extractClass.flatMap(el => Option(el.getQualifiedName))))
        typeConstruction
    }
  }
}

case class ParametrizedConstruction(iNode: IntermediateNode, parts: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  var associationMap = Seq.empty[(IntermediateNode, Option[String])]

  override def getType: TypeConstruction = iNode.asInstanceOf[TypedElement].getType
}

case class ArrayConstruction(iNode: IntermediateNode) extends IntermediateNode with TypedElement {
  var associationMap = Seq.empty[(IntermediateNode, Option[String])]

  override def getType: TypeConstruction = iNode.asInstanceOf[TypedElement].getType
}

case class DisjunctionTypeConstructions(parts: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  override def getType: TypeConstruction = this.asInstanceOf[TypeConstruction]
}

case class TypeParameterConstruction(name: IntermediateNode, typez: Seq[IntermediateNode]) extends IntermediateNode

case class TypeParameters(data: Seq[IntermediateNode]) extends IntermediateNode