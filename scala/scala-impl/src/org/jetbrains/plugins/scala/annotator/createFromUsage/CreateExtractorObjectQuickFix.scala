package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createMethodFromText

/**
 * Nikolay.Tropin
 * 2014-07-31
 */
class CreateExtractorObjectQuickFix(ref: ScReferenceElement, p: ScPattern)
        extends CreateTypeDefinitionQuickFix(ref, "extractor object", Object) {

  override protected def afterCreationWork(clazz: ScTypeDefinition) = {
    addUnapplyMethod(clazz)
    super.afterCreationWork(clazz)
  }

  override protected def addMoreElementsToTemplate(builder: TemplateBuilder, clazz: ScTypeDefinition): Unit = {
    val method = clazz.members match {
      case Seq(fun: ScFunction) => fun
      case _ => return
    }

    addQmarksToTemplate(method, builder)
    addParametersToTemplate(method, builder)
    addUnapplyResultTypesToTemplate(method, builder)
  }

  private def addUnapplyMethod(clazz: ScTypeDefinition): Unit = {
    val methodText = unapplyMethodText(p)
    val method = createMethodFromText(methodText)(clazz.getManager)
    clazz.addMember(method, None)
  }
}
