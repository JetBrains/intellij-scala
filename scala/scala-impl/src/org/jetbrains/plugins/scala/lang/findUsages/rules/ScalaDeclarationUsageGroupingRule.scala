package org.jetbrains.plugins.scala.lang.findUsages.rules

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.usages.rules.{PsiElementUsage, SingleParentUsageGroupingRule}
import com.intellij.usages.{Usage, UsageGroup, UsageInfo2UsageAdapter, UsageTarget}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.findUsages.rules.ScalaDeclarationUsageGroupingRule.getPsiElementAndContainingScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}


/**
 * Current implementation is mostly inspired by Kotlin implementation which is simpler then Java.
 * For example it doesn't show all intermediate nodes and doesn't show extra noise such as method parameter types.
 *
 * @see [[https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/kotlin.searching/base/src/org/jetbrains/kotlin/idea/base/searching/usages/KotlinDeclarationGroupRuleProvider.kt]]
 * @see [[com.intellij.usages.impl.rules.ClassGroupingRule]]
 */
private final class ScalaDeclarationUsageGroupingRule(forTypeDefinition: Boolean) extends SingleParentUsageGroupingRule with DumbAware {

  override def getParentGroupFor(usage: Usage, targets: Array[UsageTarget]): UsageGroup = {
    val (psiElement, _) = getPsiElementAndContainingScalaFile(usage) match {
      case Some(value) => value
      case None => return null
    }

    val parents: Seq[ScMember] = psiElement.parents
      .filterByType[ScMember]
      .filterNot(_.isLocal)
      .filter {
        case n: ScNamedElement => n.nameId != null
        case v: ScValueOrVariable => v.declaredNames.size == 1 //show only simple val/var with single declaration
        case _ => false
      }.toSeq

    val memberToShowInGroup = if (forTypeDefinition)
      parents.dropWhile(x => !x.is[ScTypeDefinition]).headOption
    else
      parents.headOption.filterNot(_.is[ScTypeDefinition])

    memberToShowInGroup match {
      case Some(td: ScTypeDefinition) =>
        new ScalaTypeDefinitionUsageGroup(td)
      case Some(named: ScNamedElement) =>
        new ScalaNonTypeDefinitionUsageGroup(named, named.name)
      case Some(named: ScValueOrVariable) =>
        new ScalaNonTypeDefinitionUsageGroup(named, named.declaredNames.head)
      case _ =>
        null
    }
  }
}

private object ScalaDeclarationUsageGroupingRule {
  private def getPsiElementAndContainingScalaFile(usage: Usage): Option[(PsiElement, ScFile)] = {
    val psiElementOriginal = usage match {
      case p: PsiElementUsage => p.getElement
      case _ =>
        return None
    }
    val containingFile = psiElementOriginal.getContainingFile
    if (containingFile == null)
      return None

    val injectedLanguageManager = InjectedLanguageManager.getInstance(containingFile.getProject)
    val topLevelScalaFile = injectedLanguageManager.getTopLevelFile(containingFile) match {
      case f: ScFile if f.isPhysical => f
      case _ =>
        return None
    }

    val psiElement: PsiElement =
      if (topLevelScalaFile eq containingFile) psiElementOriginal
      else injectedLanguageManager.getInjectionHost(containingFile)

    /**
     * Not sure what this is for, but similar code was in Java and Kotlin implementations
     * (see example in [[com.intellij.usages.impl.rules.MethodGroupingRule.getParentGroupFor]])
     */
    val psiElementFinal = usage match {
      case adapter: UsageInfo2UsageAdapter if psiElement eq topLevelScalaFile =>
        val offset = adapter.getUsageInfo.getNavigationOffset
        containingFile.findElementAt(offset)
      case _ =>
        psiElement
    }

    Some((psiElementFinal, topLevelScalaFile))
  }
}