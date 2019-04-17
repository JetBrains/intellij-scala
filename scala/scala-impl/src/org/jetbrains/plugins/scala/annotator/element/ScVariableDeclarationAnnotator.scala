package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkAbstractMemberPrivateModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDeclaration

object ScVariableDeclarationAnnotator extends ElementAnnotator[ScVariableDeclaration] {
  override def annotate(element: ScVariableDeclaration, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    checkAbstractMemberPrivateModifier(element, element.declaredElements.map(_.nameId), holder)
  }
}
