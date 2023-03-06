package org.jetbrains.plugins.scala.lang.structureView // Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.ide.structureView.impl.java._
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.util.PropertyOwner
import com.intellij.psi.PsiElement
import com.intellij.ui.{IconManager, PlatformIcons}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.structureView.ScalaAnonymousClassesNodeProvider.getAnonymousClassElements
import org.jetbrains.plugins.scala.lang.structureView.element.ScalaAnonymousClassTreeElement

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
      case el: org.jetbrains.plugins.scala.lang.structureView.element.Element =>
        val psiElement = el.element
        if (psiElement != null) {
          val anonimousClasses = getAnonymousClassElements(psiElement)
          val nodes = anonimousClasses.map(new ScalaAnonymousClassTreeElement(_))
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

  private def getAnonymousClassElements(parent0: PsiElement): Seq[ScNewTemplateDefinition] = {
    val elementToStartFromOpt = parent0 match {
      case td: ScTemplateDefinition       => td.extendsBlock.templateBody
      case f: ScFunctionDefinition        => f.body
      case v: ScValueOrVariableDefinition => v.expr
      case _                              => Some(parent0)
    }
    val elementToStartFrom = elementToStartFromOpt match {
      case Some(value) => value
      case None =>
        return Nil
    }

    val elementsIterator = elementToStartFrom.depthFirst { element =>
      val processChildren = element match {
        //Template body of every type definition starts it's own scope of anonimous
        //Template definition has constructor invocation, and anonimous class can be inside constructor arguments
        //So instead of stopping at template definition node we will stop at template body node
        case _: ScTemplateDefinition                      => true
        case _: ScTemplateBody | _: ScMember | _: ScBlock => false
        case _                                            => true
      }
      processChildren ||
        (element eq elementToStartFrom) //don't skip children of the node from which we started the traversal
    }

    elementsIterator
      .filterByType[ScNewTemplateDefinition]
      .filter(_.isAnonimous)
      .toSeq
  }
}