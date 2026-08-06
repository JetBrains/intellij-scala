package org.jetbrains.plugins.scala.hierarchy

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.hierarchy.`type`.JavaTypeHierarchyProvider
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.Constructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition

final class ScalaTypeHierarchyProvider extends JavaTypeHierarchyProvider {
  override def getTarget(dataContext: DataContext): PsiClass = {
    val editor = Option(CommonDataKeys.EDITOR.getData(dataContext))

    editor
      .flatMap(getScalaTarget)
      .getOrElse(super.getTarget(dataContext))
  }

  private def getScalaTarget(editor: Editor): Option[PsiClass] = {
    val targetElement = TargetElementUtil.findTargetElement(editor,
      TargetElementUtil.ELEMENT_NAME_ACCEPTED |
        TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
        TargetElementUtil.LOOKUP_ITEM_ACCEPTED
    )

    targetElement match {
      case element: PsiClass =>
        Some(element)
      case Constructor.ofClass(clazz) =>
        Some(clazz)
      case alias: ScTypeAliasDefinition =>
        alias.aliasedType.toOption
          .flatMap(_.extractClass)
      case _ =>
        None
    }
  }
}
