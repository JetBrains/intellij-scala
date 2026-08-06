package org.jetbrains.plugins.scala.structureView // Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.ide.structureView.impl.java.*
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.util.PropertyOwner
import com.intellij.ui.{IconManager, PlatformIcons}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.structureView.ScalaAnonymousClassesNodeProvider.getAnonymousClassElements
import org.jetbrains.plugins.scala.structureView.element.{Element, ScalaAnonymousClassTreeElement, ValOrVar}

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * Created by analog with [[com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider]]
 */
final class ScalaAnonymousClassesNodeProvider
  extends FileStructureNodeProvider[ScalaAnonymousClassTreeElement]
    with PropertyOwner {

  override def getName: String = ScalaAnonymousClassesNodeProvider.ID

  override def getCheckBoxText: String = JavaStructureViewBundle.message("file.structure.toggle.show.anonymous.classes")

  override def getPresentation: ActionPresentationData =
    new ActionPresentationData(getCheckBoxText, null, IconManager.getInstance.getPlatformIcon(PlatformIcons.AnonymousClass))

  override def getShortcut: Array[Shortcut] = Array()

  override def getPropertyName: String = ScalaAnonymousClassesNodeProvider.SCALA_ANONYMOUS_PROPERTY_NAME

  override def provideNodes(node: TreeElement): util.Collection[ScalaAnonymousClassTreeElement] = {
    node match {
      case element: Element =>
        if (element.element != null) {
          val anonymousClasses = getAnonymousClassElements(element)
          val nodes = anonymousClasses.map(new ScalaAnonymousClassTreeElement(_))
          nodes.asJava
        }
        else Collections.emptyList
      case _ =>
        Collections.emptyList
    }
  }
}

object ScalaAnonymousClassesNodeProvider {
  val ID = "SCALA_SHOW_ANONYMOUS"
  private val SCALA_ANONYMOUS_PROPERTY_NAME = "scala.anonymous.provider"

  private def getAnonymousClassElements(parent: Element): Seq[ScNewTemplateDefinition] = {
    val elementToStartFromOpt = parent match {
      case value: ValOrVar => value.parent match {
        case definition: ScValueOrVariableDefinition if definition.declaredElements.size == 1 => definition.expr
        case _ => None
      }
      case _ => parent.element match {
        case definition: ScTemplateDefinition => definition.extendsBlock.templateBody
        case definition: ScFunctionDefinition => definition.body
        case element => Some(element)
      }
    }
    val elementToStartFrom = elementToStartFromOpt match {
      case Some(value) => value
      case None =>
        return Nil
    }

    val childScopes = parent.getChildren.iterator.flatMap {
      case value: ValOrVar =>
        Option.when(value.parent.declaredElements.size == 1)(value.parent)
      case child: Element =>
        Some(child.element)
      case _ =>
        None
    }.toSet

    elementToStartFrom.depthFirst { element =>
      (element eq elementToStartFrom) || (element match {
        case _: ScTemplateBody => false
        case _ => !childScopes.contains(element)
      })
    }.collect {
      case definition: ScNewTemplateDefinition if definition.isAnonymous => definition
    }.toSeq
  }
}
