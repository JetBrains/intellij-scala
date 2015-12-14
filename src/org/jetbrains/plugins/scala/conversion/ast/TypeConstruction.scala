package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import org.jetbrains.plugins.scala.lang.psi.types.{JavaArrayType, ScParameterizedType, ScType}

import scala.collection.mutable.ArrayBuffer

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

  override def getType: IntermediateNode = this
}

object TypeConstruction {
  def createIntermediateTypePresentation(inType: PsiType, inProject: Project): IntermediateNode = {
    val buffer = new ArrayBuffer[(IntermediateNode, Option[String])]()
    val result = getParts(ScType.create(inType, inProject, paramTopLevel = true), buffer)

    result match {
      case parametrized: ParametrizedConstruction =>
        parametrized.assocoationMap = buffer.toSeq
        parametrized
      case array: ArrayConstruction =>
        array.assocoationMap = buffer.toSeq
        array
      case _ => result
    }
  }

  // get simple parts of type if type is array or parametrized
  def getParts(scType: ScType, buffer: ArrayBuffer[(IntermediateNode, Option[String])]): IntermediateNode = {
    scType match {
      case p@ScParameterizedType(des, args) =>
        val typeConstruction: IntermediateNode = TypeConstruction(ScType.presentableText(des, noPrefix = true))
        buffer += ((typeConstruction, ScType.extractClass(p).flatMap(el => Option(el.getQualifiedName))))
        val argsOnLevel = args.map(getParts(_, buffer))
        ParametrizedConstruction(typeConstruction, argsOnLevel)
      case JavaArrayType(arg) =>
        ArrayConstruction(getParts(arg, buffer))
      case otherType =>
        val typeConstruction: IntermediateNode = TypeConstruction(ScType.presentableText(otherType, noPrefix = true))
        buffer += ((typeConstruction, ScType.extractClass(otherType).flatMap(el => Option(el.getQualifiedName))))
        typeConstruction
    }
  }
}

case class ParametrizedConstruction(iNode: IntermediateNode, parts: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  var assocoationMap = Seq[(IntermediateNode, Option[String])]()

  def getAssociations = assocoationMap.collect { case (n, Some(value)) => (n, value) }

  override def getType: IntermediateNode = this
}

case class ArrayConstruction(iNode: IntermediateNode) extends IntermediateNode with TypedElement {
  var assocoationMap = Seq[(IntermediateNode, Option[String])]()

  def getAssociations = assocoationMap.collect { case (n, Some(value)) => (n, value) }

  override def getType: IntermediateNode = this
}

case class TypeParameterConstruction(name: IntermediateNode, typez: Seq[IntermediateNode]) extends IntermediateNode

case class TypeParameters(data: Seq[IntermediateNode]) extends IntermediateNode