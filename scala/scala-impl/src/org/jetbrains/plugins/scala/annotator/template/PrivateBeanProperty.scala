package org.jetbrains.plugins.scala.annotator.template

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{AnnotatorPart, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.annotator.quickfix.ModifierQuickFix.{MakeProtected, MakePublic}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

object PrivateBeanProperty extends AnnotatorPart[ScAnnotation] with DumbAware {

  override def annotate(annotation: ScAnnotation, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (isBeanPropertyAnnotation(annotation)) {

      PsiTreeUtil.getParentOfType(annotation, classOf[ScMember]) match {
        case property: ScValueOrVariable =>
          for {
            privateModifier <- property.getModifierList.accessModifier
            if privateModifier.isPrivate
          } {
            val fixes = Seq(new MakePublic(property), new MakeProtected(property))
            holder.createErrorAnnotation(
              privateModifier,
              ScalaBundle.message("annotator.error.bean.property.should.not.be.private"),
              fixes
            )
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
