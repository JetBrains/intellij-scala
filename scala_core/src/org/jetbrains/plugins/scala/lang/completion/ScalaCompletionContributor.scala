package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.TailType
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
    val messages = Array[String](
      "Local variables rename is inplace now.",
      "Scala plugin page is available now.",
      "Ctrl+Q documentation lookup was added.",
      "Ctrl+P parameter lookup was added.",
      "Perfomance was improved.",
      null
    )
    messages apply (new _root_.scala.util.Random).nextInt(messages.size)
  }
}