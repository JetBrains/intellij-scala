package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

object ScEnumCaseAnnotator extends ElementAnnotator[ScEnumCase] {
  override def annotate(
    cse:       ScEnumCase,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val enumDef = cse.enumParent
    val parents = cse.extendsBlock.toOption.flatMap(_.templateParents)

    def isDesignatedToEnumParent(tpe: ScType): Boolean =
      tpe.extractClass.filterByType[ScTypeDefinition].exists(ScEnum.isDesugaredEnumClass)

    val nonVariantTypeParameter =
      enumDef.typeParameters.find(_.variance.isInvariant)

    //invariant type parameters in enum class require explicit
    //extends clause for each enum case, while covariant/contravariant ones
    //are just minimized/maximized implicitly
    val isSingletonCase = cse match {
      case ScEnumCase.SingletonCase(_, _) => true
      case _                              => false
    }

    if (isSingletonCase) {
      nonVariantTypeParameter.foreach { tp =>
        if (parents.isEmpty)
          holder.createErrorAnnotation(
            cse.nameId,
            ScalaBundle.message("annotator.error.enum.nonvariant.type.param,in.enum", enumDef.name, tp.name)
          )
      }
    }

    val needsExplicitExtendsParent =
      parents.exists(_.superTypes.forall(!isDesignatedToEnumParent(_)))

    //if an enum case has an explicit extends clause it must extend its parent enum class
    if (needsExplicitExtendsParent) {
      holder.createErrorAnnotation(
        cse.extendsBlock,
        ScalaBundle.message("annotator.error.enum.case.must.extend.parent", enumDef.name)
      )
    }

    //if both enum class and enum case have type parameters
    //an explicit extends clause must be provided
    if (enumDef.hasTypeParameters && cse.physicalTypeParameters.nonEmpty && parents.isEmpty) {
      holder.createErrorAnnotation(
        cse.nameId,
        ScalaBundle.message("annotator.error.enum.two.type.parameter.clauses")
      )
    }
  }
}
