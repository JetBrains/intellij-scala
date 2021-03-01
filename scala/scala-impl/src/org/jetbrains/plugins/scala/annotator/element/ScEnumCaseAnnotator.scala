package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

object ScEnumCaseAnnotator extends ElementAnnotator[ScEnumCase] {
  override def annotate(
    cse:   ScEnumCase,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val enum    = cse.enumParent
    val parents = cse.physicalExtendsBlock.toOption.flatMap(_.templateParents)

    def isDesignatedToEnumParent(tpe: ScType): Boolean =
      tpe.extractClass.exists {
        case ScEnum.DesugaredEnumClass(`enum`) => true
        case _                                 => false
      }

    val nonVariantTypeParameter =
      enum.typeParameters.find(_.variance.isInvariant)

    //invariant type parameters in enum class require explicit
    //extends clause for each enum case, while covariant/contravariant ones
    //are just minimized/maximized implicitly
    nonVariantTypeParameter.foreach { tp =>
      if (parents.isEmpty)
        holder.createErrorAnnotation(
          cse.nameId,
          ScalaBundle.message("annotator.error.enum.nonvariant.type.param,in.enum", enum.name, tp.name)
        )
    }

    val needsExplicitExtendsParent =
      parents.exists(_.superTypes.forall(!isDesignatedToEnumParent(_)))

    //if an enum case has an explicit extends clause it must extend its parent enum class
    if (needsExplicitExtendsParent) {
      holder.createErrorAnnotation(
        cse.physicalExtendsBlock,
        ScalaBundle.message("annotator.error.enum.case.must.extend.parent", enum.name)
      )
    }

    //if both enum class and enum case have type parameters
    //an explicit extends clause must be provided
    if (enum.hasTypeParameters && cse.physicalTypeParameters.nonEmpty && parents.isEmpty) {
      holder.createErrorAnnotation(
        cse.nameId,
        ScalaBundle.message("annotator.error.enum.two.type.parameter.clauses")
      )
    }
  }
}
