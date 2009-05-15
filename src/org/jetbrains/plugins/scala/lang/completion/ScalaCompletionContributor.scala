package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.TailType
import psi.api.expr.{ScPostfixExpr, ScInfixExpr, ScReferenceExpression}
import psi.api.ScalaFile;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.patterns.ElementPattern;

/**
 * @author Alexander Podkhalyuzin
 * Date: 16.05.2008
 */

class ScalaCompletionContributor extends CompletionContributor {
  override def advertise(parameters: CompletionParameters): String = {
    if (!parameters.getOriginalFile.isInstanceOf[ScalaFile]) return null
    val messages = Array[String]( //todo:
      null
      )
    messages apply (new _root_.scala.util.Random).nextInt(messages.size)
  }


  override def beforeCompletion(context: CompletionInitializationContext) = {
    val rulezzz = CompletionInitializationContext.DUMMY_IDENTIFIER
    val offset = context.getStartOffset() - 1
    val file = context.getFile
    val element = file.findElementAt(offset);
    if (element != null && file.findReferenceAt(offset) != null && specialOperator(element.getParent)) {
      context.setFileCopyPatcher(new DummyIdentifierPatcher("+"));
    }
    super.beforeCompletion(context)
  }

  def specialOperator(elem: PsiElement) = (elem match {
    case ref: ScReferenceExpression => ref.getParent match {
      case inf: ScInfixExpr if ref eq inf.operation => true
      case pos: ScPostfixExpr if ref eq pos.operation => true
      case _ => false
    }
    case _ => false
  }) && !Character.isJavaIdentifierPart(elem.getText.charAt(0))


}