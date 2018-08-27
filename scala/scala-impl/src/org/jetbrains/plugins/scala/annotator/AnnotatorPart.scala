package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

import scala.reflect.ClassTag

/**
  * Pavel Fatin
  */
abstract class AnnotatorPart[T <: ScalaPsiElement : ClassTag] {
  def annotate(element: T,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit
}

abstract class TemplateDefinitionAnnotatorPart extends AnnotatorPart[ScTemplateDefinition]

/*{

 protected case class Descriptor(range: TextRange,
                                 message: String,
                                 severity: HighlightSeverity = HighlightSeverity.ERROR)

 override final def annotate(definition: ScTemplateDefinition,
                             holder: AnnotationHolder,
                             typeAware: Boolean): Unit = for {
   Descriptor(range, message, severity) <- collectProblemDescriptors(definition, typeAware)
 } holder.createAnnotation(severity, range, message)

 protected def collectProblemDescriptors(definition: ScTemplateDefinition,
                                         typeAware: Boolean): List[Descriptor]

// TODO 1) collectSuperRefs should be a protected method
// TODO 2) superRefs method should be called once
}*/