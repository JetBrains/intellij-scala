package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
package object api {

  implicit class TypeParametersArrayExt(val typeParameters: Array[TypeParameter]) extends AnyVal {
    def depth: Int = typeParameters.toSeq.depth
  }

  implicit class TypeParametersSeqExt(val typeParameters: Seq[TypeParameter]) extends AnyVal {
    def depth: Int = {
      def depth(tp: TypeParameter): Int = Seq(tp.lowerType.typeDepth, tp.upperType.typeDepth, tp.typeParameters.depth).max

      val maxDepth = if (typeParameters.isEmpty) 0 else typeParameters.map(depth).max
      1 + maxDepth
    }
  }

  implicit class PsiTypeParamatersExt(val typeParameters: Array[PsiTypeParameter]) extends AnyVal {
    def instantiate: Seq[TypeParameter] = typeParameters match {
      case Array() => Seq.empty
      case array => array.toSeq.map(TypeParameter(_))
    }
  }

  def Any(implicit pc: ProjectContext) = StdTypes.instance.Any

  def AnyRef(implicit pc: ProjectContext) = StdTypes.instance.AnyRef

  def Null(implicit pc: ProjectContext) = StdTypes.instance.Null

  def Nothing(implicit pc: ProjectContext) = StdTypes.instance.Nothing

  def Singleton(implicit pc: ProjectContext) = StdTypes.instance.Singleton

  def AnyVal(implicit pc: ProjectContext) = StdTypes.instance.AnyVal

  def Unit(implicit pc: ProjectContext) = StdTypes.instance.Unit

  def Boolean(implicit pc: ProjectContext) = StdTypes.instance.Boolean

  def Char(implicit pc: ProjectContext) = StdTypes.instance.Char

  def Byte(implicit pc: ProjectContext) = StdTypes.instance.Byte

  def Short(implicit pc: ProjectContext) = StdTypes.instance.Short

  def Int(implicit pc: ProjectContext) = StdTypes.instance.Int

  def Long(implicit pc: ProjectContext) = StdTypes.instance.Long

  def Float(implicit pc: ProjectContext) = StdTypes.instance.Float

  def Double(implicit pc: ProjectContext) = StdTypes.instance.Double

  val Bivariant     = Variance.Bivariant
  val Covariant     = Variance.Covariant
  val Contravariant = Variance.Contravariant
  val Invariant     = Variance.Invariant
}
