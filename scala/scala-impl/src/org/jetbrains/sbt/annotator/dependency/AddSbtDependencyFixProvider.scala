package org.jetbrains.sbt.annotator.dependency

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.extensions.{BooleanExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

/**
  * @author Pavel Fatin
  */
class AddSbtDependencyFixProvider extends UnresolvedReferenceFixProvider {
  override def fixesFor(reference: ScReference): Seq[IntentionAction] = {
    val isInsideImportStatement = PsiTreeUtil.getParentOfType(reference, classOf[ScImportExpr]) != null
    isInsideImportStatement.seq(new AddSbtDependencyFix(reference.createSmartPointer))
  }
}
