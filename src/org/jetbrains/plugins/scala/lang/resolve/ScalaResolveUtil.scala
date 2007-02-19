package org.jetbrains.plugins.scala.lang.resolve

/** 
* @author Ilya Sergey
*
*/

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.scope.PsiScopeProcessor

import org.jetbrains.plugins.scala.lang.resolve.processors._

object ScalaResolveUtil {

  /**
  * Main function that walks up by PSI Tree an finds declaration using processor
  *
  */
  def treeWalkUp(processor: PsiScopeProcessor,
          elt: PsiElement,
          lastParent: PsiElement,
          place: PsiElement): PsiElement = {
    if (processor.isInstanceOf[ScalaPsiScopeProcessor]) {

      if (elt == null) return null
      var cur = elt
      while (cur.processDeclarations(processor,
              PsiSubstitutor.EMPTY,
              if (cur == elt) lastParent else null,
              place)) {
        if (cur.isInstanceOf[PsiFile]) return null
        cur = cur.getParent
      }

      val result = processor.asInstanceOf[ScalaPsiScopeProcessor].getResult
      result

    } else null


  }

  /*
  35     @Nullable
  36     public static PsiElement treeWalkUp(PsiScopeProcessor processor, PsiElement elt, PsiElement lastParent, PsiElement place) {
  38
  39       PsiElement cur = elt;
  40       do {
  41         if (!cur.processDeclarations(processor, PsiSubstitutor.EMPTY, cur == elt ? lastParent : null, place)) {
  42           if (processor instanceof ResolveProcessor) {
  43             return ((ResolveProcessor)processor).getResult();
  44           }
  45         }
  46         if(cur instanceof PsiFile) break;
  47         if (cur instanceof JSStatement && cur.getContext() instanceof JSIfStatement) {
  48           // Do not try to resolve variables from then branch in else branch.
  49           break;
  50         }
  51
  52
  53         cur = cur.getPrevSibling();
  54       } while (cur != null);
  55
  56       final PsiElement func = processFunctionDeclarations(processor, elt.getContext());
  57       if (func != null) return func;
  58
  59       return treeWalkUp(processor, elt.getContext(), elt, place);
  60     }
  */


}