package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkAbstractMemberPrivateModifier
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDeclaration


trait ScVariableDeclarationAnnotator extends Annotatable { self: ScVariableDeclaration =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    checkAbstractMemberPrivateModifier(this, declaredElements.map(_.nameId), holder)
  }
}
