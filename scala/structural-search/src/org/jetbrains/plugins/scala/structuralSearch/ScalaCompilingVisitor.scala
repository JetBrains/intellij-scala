package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.dupLocator.util.DuplocatorUtil
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

class ScalaCompilingVisitor(globalVisitor: GlobalCompilingVisitor) extends ScalaElementVisitor {

  def compile(topLevelElements: Array[PsiElement]): Unit = {
    globalVisitor.getContext.getPattern.setStrategy(new MatchingStrategy() {
      // Determine if we should match on this node
      // --> use it to only select nodes of the correct language
      override def continueMatching(start: PsiElement): Boolean =
        start.getLanguage match {
          case ScalaLanguage.INSTANCE | Scala3Language.INSTANCE => true
          case _ => false
        }

      override def shouldSkip(element: PsiElement, elementToMatchWith: PsiElement): Boolean =
        DuplocatorUtil.shouldSkip(element, elementToMatchWith)
    })

    val context = globalVisitor.getContext
    val pattern = context.getPattern
    for (element <- topLevelElements) {
      // activate this if we want to use the visitor
      // element.accept(this)
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)))
    }
  }
  
  // TODO could add filter to only match this pattern node to matching nodes, e.g. comment on comment
}
