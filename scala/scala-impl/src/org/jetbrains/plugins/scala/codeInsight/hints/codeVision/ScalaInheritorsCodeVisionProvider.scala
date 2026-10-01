//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.plugins.scala.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase.CodeVisionInfo
import com.intellij.codeInsight.hints.codeVision.InheritorsCodeVisionProvider
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager}
import com.intellij.psi.{PsiClass, PsiElement, PsiFile}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.gutter.ScalaMarkerType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

import java.awt.event.MouseEvent
import java.util
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shows the number of inheritors of Scala classes and traits, and the number of overrides or implementations of their members,
 * like `JavaInheritorsCodeVisionProvider` does for Java.
 */
final class ScalaInheritorsCodeVisionProvider extends InheritorsCodeVisionProvider {

  import ScalaCodeVision.CountingLimit
  import ScalaInheritorsCodeVisionProvider._

  override def acceptsFile(file: PsiFile): Boolean = ScalaCodeVision.acceptsFile(file)

  override def acceptsElement(element: PsiElement): Boolean = element match {
    case td: ScTypeDefinition => td.is[ScClass, ScTrait] && !td.isEffectivelyFinal
    case member: ScMember if member.is[ScFunction, ScValueOrVariable, ScTypeAlias] =>
      member.containingClass != null && !member.isEffectivelyFinal
    case _ => false
  }

  override def getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionInfo = {
    val count = element match {
      case td: ScTypeDefinition => classInheritorsCount(td)
      case member: ScMember     => overridingMembersCount(member)
      case _                    => 0
    }
    if (count <= 0) null
    else {
      val shown = count.min(CountingLimit)
      val isExact = shown == count
      val kind = element match {
        case _: ScTrait                   => "implementations"
        case _: ScTypeDefinition          => "inheritors"
        case member if isAbstract(member) => "implementations"
        case _                            => "overrides"
      }
      val key = if (isExact) s"code.vision.$kind.hint" else s"code.vision.$kind.too.many.hint"
      new CodeVisionInfo(ScalaBundle.message(key, shown), shown, isExact)
    }
  }

  override def getHint(element: PsiElement, file: PsiFile): String =
    Option(getVisionInfo(element, file)).map(_.getText).orNull

  override def handleClick(editor: Editor, element: PsiElement, event: MouseEvent): Unit = {
    val dumbModeMessage = element match {
      case _: PsiClass => ScalaBundle.message("notification.navigation.to.overriding.classes")
      case _           => ScalaBundle.message("notification.navigation.to.overriding.members")
    }
    val handler = new GotoImplementationHandler
    if (event != null)
      handler.navigateToImplementations(element, event, dumbModeMessage)
    else {
      //invoked from the keyboard: behave like "Go To Implementation" on the declaration name
      editor.getCaretModel.moveToOffset(element.getTextOffset)
      handler.invoke(element.getProject, editor, element.getContainingFile)
    }
  }

  override def getId: String = ScalaCodeVision.InheritorsId

  override def getRelativeOrderings: util.List[CodeVisionRelativeOrdering] = util.List.of()
}

private object ScalaInheritorsCodeVisionProvider {

  private def isAbstract(element: PsiElement): Boolean = element match {
    case _: ScFunctionDeclaration | _: ScValueDeclaration | _: ScVariableDeclaration | _: ScTypeAliasDeclaration => true
    case _ => false
  }

  private def classInheritorsCount(definition: ScTypeDefinition): Int =
    CachedValuesManager.getCachedValue(definition, new CachedValueProvider[Integer] {
      override def compute(): CachedValueProvider.Result[Integer] = {
        val count = new AtomicInteger()
        ClassInheritorsSearch.search(definition, definition.getUseScope, true).forEach(new Processor[PsiClass] {
          override def process(inheritor: PsiClass): Boolean = inheritor match {
            case _: PsiClassWrapper => true
            //stop after the limit, the hint shows "N+" in this case
            case _                  => count.incrementAndGet() <= ScalaCodeVision.CountingLimit
          }
        })
        CachedValueProvider.Result.create(Integer.valueOf(count.get()), ScalaPsiManager.instance(definition.getProject).TopLevelModificationTracker)
      }
    }).intValue()

  private def overridingMembersCount(member: ScMember): Int =
    CachedValuesManager.getCachedValue(member, new CachedValueProvider[Integer] {
      override def compute(): CachedValueProvider.Result[Integer] = {
        val count = ScalaMarkerType.findOverrides(member, deep = true).size
        CachedValueProvider.Result.create(Integer.valueOf(count), ScalaPsiManager.instance(member.getProject).TopLevelModificationTracker)
      }
    }).intValue()
}
