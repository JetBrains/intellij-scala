package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkAbstractMemberPrivateModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDeclaration

object ScVariableDeclarationAnnotator extends ElementAnnotator[ScVariableDeclaration] {

  override def annotate(element: ScVariableDeclaration, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {
    checkAbstractMemberPrivateModifier(element, element.declaredElements.map(_.nameId))
  }
}
