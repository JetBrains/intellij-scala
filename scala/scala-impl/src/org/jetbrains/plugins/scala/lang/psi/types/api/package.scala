package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
package object api {

  implicit class TypeParametersArrayExt(private val typeParameters: Array[TypeParameter]) extends AnyVal {
    def depth: Int = typeParameters.toSeq.depth
  }

  implicit class TypeParametersSeqExt(private val typeParameters: collection.Seq[TypeParameter]) extends AnyVal {
    def depth: Int = {
      def depth(tp: TypeParameter): Int = Seq(tp.lowerType.typeDepth, tp.upperType.typeDepth, tp.typeParameters.depth).max

      val maxDepth = if (typeParameters.isEmpty) 0 else typeParameters.map(depth).max
      1 + maxDepth
    }
  }

  implicit class PsiTypeParamatersExt(private val typeParameters: Array[PsiTypeParameter]) extends AnyVal {
    def instantiate: Seq[TypeParameter] = typeParameters match {
      case Array() => Seq.empty
      case array => array.toSeq.map(TypeParameter(_))
    }
  }

  def Any(implicit pc: ProjectContext): StdType = StdTypes.instance.Any

  def AnyRef(implicit pc: ProjectContext): StdType = StdTypes.instance.AnyRef

  def Null(implicit pc: ProjectContext): StdType = StdTypes.instance.Null

  def Nothing(implicit pc: ProjectContext): StdType = StdTypes.instance.Nothing

  def Singleton(implicit pc: ProjectContext): StdType = StdTypes.instance.Singleton

  def AnyVal(implicit pc: ProjectContext): StdType = StdTypes.instance.AnyVal

  def Unit(implicit pc: ProjectContext): ValType = StdTypes.instance.Unit

  def Boolean(implicit pc: ProjectContext): ValType = StdTypes.instance.Boolean

  def Char(implicit pc: ProjectContext): ValType = StdTypes.instance.Char

  def Byte(implicit pc: ProjectContext): ValType = StdTypes.instance.Byte

  def Short(implicit pc: ProjectContext): ValType = StdTypes.instance.Short

  def Int(implicit pc: ProjectContext): ValType = StdTypes.instance.Int

  def Long(implicit pc: ProjectContext): ValType = StdTypes.instance.Long

  def Float(implicit pc: ProjectContext): ValType = StdTypes.instance.Float

  def Double(implicit pc: ProjectContext): ValType = StdTypes.instance.Double

  val Bivariant: Variance = Variance.Bivariant
  val Covariant: Variance = Variance.Covariant
  val Contravariant: Variance = Variance.Contravariant
  val Invariant: Variance = Variance.Invariant
}
