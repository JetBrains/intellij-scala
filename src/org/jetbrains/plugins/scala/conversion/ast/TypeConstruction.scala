package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScTypePresentation}
import org.jetbrains.plugins.scala.project.ProjectExt

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

  override def getType: TypeConstruction = this.asInstanceOf[TypeConstruction]
}

object TypeConstruction {
  def createIntermediateTypePresentation(inType: PsiType, inProject: Project)(implicit textMode: Boolean = false): IntermediateNode = {
    val buffer = new ArrayBuffer[(IntermediateNode, Option[String])]()
    implicit val typeSystem = inProject.typeSystem
    val result = getParts(inType.toScType(paramTopLevel = true), buffer)

    result match {
      case parametrized: ParametrizedConstruction =>
        parametrized.assocoationMap = buffer
        parametrized
      case array: ArrayConstruction =>
        array.assocoationMap = buffer
        array
      case _ => result
    }
  }

  // get simple parts of type if type is array or parametrized
  def getParts(scType: ScType, buffer: ArrayBuffer[(IntermediateNode, Option[String])])
              (implicit typeSystem: TypeSystem,
               textMode: Boolean = false): IntermediateNode = {
    scType match {
      case p@ParameterizedType(des, args) =>
        val typeConstruction: IntermediateNode = TypeConstruction(ScTypePresentation.presentableText(des, withPrefix = textMode))
        buffer += ((typeConstruction, p.extractClass().flatMap(el => Option(el.getQualifiedName))))
        val argsOnLevel = args.map(getParts(_, buffer))
        ParametrizedConstruction(typeConstruction, argsOnLevel)
      case JavaArrayType(argument) => ArrayConstruction(getParts(argument, buffer))
      case otherType =>
        val typeConstruction: IntermediateNode = TypeConstruction(ScTypePresentation.presentableText(otherType, withPrefix = textMode))
        buffer += ((typeConstruction, otherType.extractClass().flatMap(el => Option(el.getQualifiedName))))
        typeConstruction
    }
  }
}

case class ParametrizedConstruction(iNode: IntermediateNode, parts: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  var assocoationMap = Seq[(IntermediateNode, Option[String])]()

  def getAssociations: Seq[(IntermediateNode, String)] = assocoationMap.collect { case (n, Some(value)) => (n, value) }

  override def getType: TypeConstruction = iNode.asInstanceOf[TypedElement].getType
}

case class ArrayConstruction(iNode: IntermediateNode) extends IntermediateNode with TypedElement {
  var assocoationMap = Seq[(IntermediateNode, Option[String])]()

  def getAssociations: Seq[(IntermediateNode, String)] = assocoationMap.collect { case (n, Some(value)) => (n, value) }

  override def getType: TypeConstruction =
    iNode.asInstanceOf[TypedElement].getType
}

case class TypeParameterConstruction(name: IntermediateNode, typez: Seq[IntermediateNode]) extends IntermediateNode

case class TypeParameters(data: Seq[IntermediateNode]) extends IntermediateNode