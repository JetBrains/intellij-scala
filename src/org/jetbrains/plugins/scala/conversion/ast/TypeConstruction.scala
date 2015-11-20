package org.jetbrains.plugins.scala.conversion.ast

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiType
import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.lang.psi.types.{JavaArrayType, ScParameterizedType, ScType}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 10/22/15
  */
case class TypeConstruction(inType: String) extends IntermediateNode with TypedElement {
  private var printRange = new TextRange(0, 0)

  override def print(printer: PrettyPrinter): PrettyPrinter = {
    val before = printer.length
    printer.append(inType)
    printRange = new TextRange(before, printer.length)
    printer
  }

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

  override def getRange = printRange

  override def getType: TypeConstruction = this.asInstanceOf[TypeConstruction]
}


case class TypeConstructions(node: IntermediateNode, parts: (Seq[String], Seq[String])) extends IntermediateNode with TypedElement {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    node.print(printer)
  }


  override def getType: TypeConstruction = node.asInstanceOf[TypedElement].getType
}


case class ParametrizedConstruction(node: IntermediateNode, parts: Seq[IntermediateNode]) extends IntermediateNode with TypedElement {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    node.print(printer)
    printer.append(parts, ", ", "[", "]", parts.nonEmpty)
  }

  var assocoationMap = Seq[(IntermediateNode, Option[String])]()

  def getAssociations = assocoationMap.collect { case (n, Some(value)) => (n, value) }

  override def getType: TypeConstruction = node.asInstanceOf[TypeConstruction].getType
}

case class ArrayConstruction(node: IntermediateNode) extends IntermediateNode with TypedElement {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    printer.append("Array[")
    node.print(printer)
    printer.append("]")
  }

  var assocoationMap = Seq[(IntermediateNode, Option[String])]()

  def getAssociations = assocoationMap.collect { case (n, Some(value)) => (n, value) }

  override def getType: TypeConstruction = node.asInstanceOf[TypedElement].getType
}

object TypeConstruction {
  def createStringTypePresentation(inType: PsiType, inProject: Project): IntermediateNode = {
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
        val typeConstruction: IntermediateNode = TypeConstruction(des.presentableText)
        buffer += ((typeConstruction, ScType.extractClass(p).flatMap(el => Option(el.getQualifiedName))))
        val argsOnLevel = args.map(getParts(_, buffer))
        ParametrizedConstruction(typeConstruction, argsOnLevel)
      case JavaArrayType(arg) =>
        ArrayConstruction(getParts(arg, buffer))
      case otherType =>
        val typeConstruction: IntermediateNode = TypeConstruction(otherType.presentableText)
        buffer += ((typeConstruction, ScType.extractClass(otherType).flatMap(el => Option(el.getQualifiedName))))
        typeConstruction
    }
  }
}