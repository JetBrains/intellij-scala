package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAliasDeclaration

object ScGivenAliasDeclarationAnnotator extends ElementAnnotator[ScGivenAliasDeclaration] {
  override def annotate(decl: ScGivenAliasDeclaration, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    checkAnonymousGivenDeclaration(decl)

  private def checkAnonymousGivenDeclaration(decl: ScGivenAliasDeclaration)
                                            (implicit holder: ScalaAnnotationHolder): Unit =
    decl.nameElement match {
      case None =>
        holder.createErrorAnnotation(decl, ScalaBundle.message("given.alias.declaration.must.be.named"))
      case _ =>
    }
}
