package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkAbstractMemberPrivateModifier
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDeclaration

object ScVariableDeclarationAnnotator extends ElementAnnotator[ScVariableDeclaration] {

  override def annotate(element: ScVariableDeclaration, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    checkAbstractMemberPrivateModifier(element, element.declaredElements.map(_.nameId))
  }
}
