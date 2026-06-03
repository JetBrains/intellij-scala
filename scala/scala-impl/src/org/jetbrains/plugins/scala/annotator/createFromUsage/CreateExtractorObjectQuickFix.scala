package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createMethodFromText

class CreateExtractorObjectQuickFix(ref: ScReference, p: ScPattern)
  extends CreateTypeDefinitionQuickFix(ref, Object) {

  override val getText: String = ScalaBundle.message("create.extractor.object.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.extractor.object")

  override protected def afterCreationWork(clazz: ScTypeDefinition)(editor: Editor): Unit = {
    addUnapplyMethod(clazz)
    super.afterCreationWork(clazz)(editor)
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
    val method = createMethodFromText(methodText, clazz)(clazz.getManager)
    clazz.addMember(method, None)
  }
}
