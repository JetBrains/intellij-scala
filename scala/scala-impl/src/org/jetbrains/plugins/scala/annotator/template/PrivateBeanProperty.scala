package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
  * Nikolay.Tropin
  * 11/6/13
  */
object PrivateBeanProperty extends AnnotatorPart[ScAnnotation] {

  override def annotate(annotation: ScAnnotation, typeAware: Boolean = false)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (isBeanPropertyAnnotation(annotation)) {

      PsiTreeUtil.getParentOfType(annotation, classOf[ScMember]) match {
        case property: ScValueOrVariable =>
          for {
            privateModifier <- property.getModifierList.accessModifier
            if privateModifier.isPrivate

            errorAnnotation = holder.createErrorAnnotation(privateModifier,
              ScalaBundle.message("annotator.error.bean.property.should.not.be.private"))
          } {
            import quickfix.ModifierQuickFix._
            errorAnnotation.registerFix(new MakePublic(property))
            errorAnnotation.registerFix(new MakeProtected(property))
          }
        case _ =>
      }
    }

  private def isBeanPropertyAnnotation(annotation: ScAnnotation) =
    annotation.getText match {
      case "@BeanProperty" |
           "@BooleanBeanProperty" => true
      case _ => false
    }
}
