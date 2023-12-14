package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.{PsiClass, PsiElement, PsiFile, PsiMethod}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesConfiguration, ScalaFindUsagesHandler}
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScConstructorInvocation, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSelfInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import java.util

class ScalaHighlightConstructorInvocationUsages(reference: Option[ScReference], file: PsiFile, editor: Editor)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file)
{

  def this(invoc: ScConstructorInvocation, file: PsiFile, editor: Editor) = this(invoc.reference, file, editor)

  private val elementsToHighlight: Option[(PsiClass, Option[PsiMethod])] = reference
    .flatMap(_.bind())
    .collect {
      case ScalaResolveResult(clazz: PsiClass, _) =>
        //case 1: creation of anonymous class with trait as base: `new MyTrait {}`
        //case 2: creation of Java class (or other non-Scala language)
        (clazz, None)
      case ScalaResolveResult(Constructor(constructor), _) =>
        (constructor.containingClass, Some(constructor))
    }

  override def getTargets: util.List[PsiElement] = reference.fold(util.Collections.emptyList[PsiElement])(util.Collections.singletonList)

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
    val config = ScalaFindUsagesConfiguration.getInstance(file.getProject)
    val manager = new ScalaFindUsagesHandler(classToHighlight, config)
    val localSearchScope = new LocalSearchScope(file)

    //highlight references to the constructor or class
    val target = constructor.getOrElse(classToHighlight)
    val references = manager.findReferencesToHighlight(target, localSearchScope)
    references.forEach { ref =>
      val occurenceElement = ref match {
        case si: ScSelfInvocation => si.thisElement
        case _ => ref.getElement
      }
      addOccurrence(occurenceElement)
    }

    //highlight class name at definition side
    addOccurrence(classToHighlight.getNameIdentifier)

    //highlight secondary constructor
    val secondaryConstructors = constructor
      .filter(_.isConstructor)
      .flatMap(_.getNameIdentifier.toOption)
    secondaryConstructors.foreach(addOccurrence)
  }
}
