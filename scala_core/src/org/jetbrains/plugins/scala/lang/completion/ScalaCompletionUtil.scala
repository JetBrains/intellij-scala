package org.jetbrains.plugins.scala.lang.completion

import com.intellij.psi.PsiElement;

/** 
* User: Alexander Podkhalyuzin
* Date: 21.05.2008.
*/

object ScalaCompletionUtil {
  def getLeafByOffset(offset: Int, element: PsiElement): PsiElement = {
    if (offset < 0) {
      return null;
    }
    var candidate: PsiElement = element.getContainingFile();
    while (candidate.getNode().getChildren(null).length > 0) {
      candidate = candidate.findElementAt(offset);
    }
    return candidate;
  }
}