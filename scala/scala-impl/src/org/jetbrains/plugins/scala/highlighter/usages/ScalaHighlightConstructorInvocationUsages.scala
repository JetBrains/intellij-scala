package org.jetbrains.plugins.scala
package highlighter
package usages

import java.util

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.{PsiClass, PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesHandler, ScalaFindUsagesHandlerFactory}
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScConstructorInvocation, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScalaHighlightConstructorInvocationUsages(invoc: ScConstructorInvocation, file: PsiFile, editor: Editor)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file)
{
  private val elementsToHighlight = invoc.reference
    .flatMap(_.bind())
    .collect {
      case ScalaResolveResult(clazz: PsiClass, _) => (clazz, None)
      case ScalaResolveResult(Constructor(constructor), _) => (constructor.containingClass, Some(constructor))
    }

  override def getTargets: util.List[PsiElement] = invoc.reference.fold(util.Collections.emptyList[PsiElement])(util.Collections.singletonList)

  override def selectTargets(targets: util.List[_ <: PsiElement], selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit =
    selectionConsumer.consume(targets)

  override protected def addOccurrence(element: PsiElement): Unit = {
    if (element != null && element.getContainingFile == file)
      super.addOccurrence(element match {
        case ref: ScStableCodeReference => ref.nameId
        case e => e
      })
  }

  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = elementsToHighlight.foreach { case (classToHighlight, constructor) =>
    val project = file.getProject
    val factory = ScalaFindUsagesHandlerFactory.getInstance(project)
    val manager = new ScalaFindUsagesHandler(classToHighlight, factory)
    val localSearchScope = new LocalSearchScope(file)

    manager
      .findReferencesToHighlight(classToHighlight, localSearchScope)
      .forEach(e => addOccurrence(e.getElement))
    addOccurrence(classToHighlight.getNameIdentifier)
    constructor
      .flatMap(_.getNameIdentifier.toOption)
      .filter(_.textMatches("this"))
      .foreach(addOccurrence)
  }
}
