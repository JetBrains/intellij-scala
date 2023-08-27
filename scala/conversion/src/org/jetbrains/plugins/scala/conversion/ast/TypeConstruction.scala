package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable

case class TypeConstruction(inType: String) extends TypeNode {
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
  def createIntermediateTypePresentation(
    inType: PsiType,
    inProject: Project,
    textMode: Boolean,
  ): TypeNode = {
    implicit val ctx: Project = inProject

    val buffer = mutable.ArrayBuffer.empty[(IntermediateNode, Option[String])]
    //java Object should be treated as AnyRef, not Any
    val scType = inType.toScType(paramTopLevel = true, treatJavaObjectAsAny = false)
    val result = getParts(scType, buffer, textMode)

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
  private def getParts(
    scType: ScType,
    buffer: mutable.ArrayBuffer[(IntermediateNode, Option[String])],
    textMode: Boolean
  )(
    implicit ctx: Project
  ): TypeNode = {
    implicit val tpc: TypePresentationContext = TypePresentationContext.emptyContext
    scType match {
      case p@ParameterizedType(des, args) =>
        val typeConstruction: IntermediateNode = TypeConstruction(scType.getProject.getScalaTypeSystem.presentableText(des, withPrefix = textMode))
        buffer += ((typeConstruction, p.extractClass.flatMap(el => Option(el.getQualifiedName))))
        val argsOnLevel = args.map(getParts(_, buffer, textMode))
        ParametrizedConstruction(typeConstruction, argsOnLevel)
      case JavaArrayType(argument) =>
        ArrayConstruction(getParts(argument, buffer, textMode))
      case otherType =>
        val typeConstruction: TypeNode = TypeConstruction(scType.getProject.getScalaTypeSystem.presentableText(otherType, withPrefix = textMode))
        buffer += ((typeConstruction, otherType.extractClass.flatMap(el => Option(el.getQualifiedName))))
        typeConstruction
    }
  }
}

case class ParametrizedConstruction(iNode: IntermediateNode, parts: Seq[IntermediateNode]) extends TypeNode {
  var associationMap = Seq.empty[(IntermediateNode, Option[String])]

  override def getType: TypeConstruction = iNode.asInstanceOf[TypedElement].getType
}

case class ArrayConstruction(iNode: IntermediateNode) extends TypeNode {
  var associationMap = Seq.empty[(IntermediateNode, Option[String])]

  override def getType: TypeConstruction = iNode.asInstanceOf[TypedElement].getType
}

case class DisjunctionTypeConstructions(parts: Seq[IntermediateNode]) extends TypeNode {
  override def getType: TypeConstruction = this.asInstanceOf[TypeConstruction]
}

case class TypeParameterConstruction(name: NameIdentifier, typez: Seq[IntermediateNode]) extends IntermediateNode

case class TypeParameters(data: Seq[IntermediateNode]) extends IntermediateNode