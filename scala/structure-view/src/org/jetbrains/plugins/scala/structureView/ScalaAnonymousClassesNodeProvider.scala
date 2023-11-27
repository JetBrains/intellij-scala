package org.jetbrains.plugins.scala.structureView // Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.ide.structureView.impl.java.*
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.util.PropertyOwner
import com.intellij.psi.PsiElement
import com.intellij.ui.{IconManager, PlatformIcons}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.structureView.ScalaAnonymousClassesNodeProvider.getAnonymousClassElements
import org.jetbrains.plugins.scala.structureView.element.ScalaAnonymousClassTreeElement

import java.util
import java.util.Collections
import scala.collection.mutable
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
      case el: org.jetbrains.plugins.scala.structureView.element.Element =>
        val psiElement = el.element
        if (psiElement != null) {
          val anonymousClasses = getAnonymousClassElements(psiElement)
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

  /**
   * Note regarding val/var definitions:
   *  1. val/var definitions nodes are only created for fields, but not for local definitions: {{{
   *       class A {
   *         val value1 = 1 //has a node in structure view
   *         def foo = {
   *           val value2 = 2 //doesn't have a node in a structure view
   *         }
   *       }
   *     }}}
   *     See [[org.jetbrains.plugins.scala.structureView.element.TypeDefinition.childrenOf]]
   *     and [[org.jetbrains.plugins.scala.structureView.element.Block.childrenOf]]
   *
   *  1. var/var fields definition can have nested children only when it's body is a block expression<br>
   *     See [[org.jetbrains.plugins.scala.structureView.element.ValOrVar.children]]
   */
  private def getAnonymousClassElements(parent0: PsiElement): Seq[ScNewTemplateDefinition] = {
    val elementToStartFromOpt = parent0 match {
      case td: ScTemplateDefinition => td.extendsBlock.templateBody
      case f: ScFunctionDefinition => f.body
      case p: ScReferencePattern =>
        //We do extra `filterByType` after `findByType`, not just `findByType[ScValueOrVariableDefinition]`
        //to fail fast on declarations (abstract val/var)
        val valueOrVariableDef = p.parentsInFile.findByType[ScValueOrVariable].filterByType[ScValueOrVariableDefinition]
        for {
          case value <- valueOrVariableDef
          //If val/var definition body is not a block, we skip it. In that case anonymous classes will be collected
          //during processing of the parent node, and will be added as a sibling of val/var node (see `getAnonymousClassElements` ScalaDoc)
          case block <- value.expr.filterByType[ScBlockExpr]
        } yield block
      case _ =>
        Some(parent0)
    }
    val elementToStartFrom = elementToStartFromOpt match {
      case Some(value) => value
      case None =>
        return Nil
    }

    val result = mutable.ArrayBuffer.empty[ScNewTemplateDefinition]

    def process(element: PsiElement): Unit = {
      element match {
        case d: ScNewTemplateDefinition if d.isAnonimous =>
          result += d
        case _ =>
      }
    }

    //Collect anonymous classes in quite a dirty imperative way
    elementToStartFrom.depthFirst { element =>
      val processChildren = element match {
        //Don't stop at "ScTemplateDefinition"
        //Template body of every type definition starts it's own scope of anonymous
        //Template definition has constructor invocation, and anonymous class can be inside constructor arguments
        //So instead of stopping at template definition node we will stop at template body node
        case _: ScTemplateDefinition => true
        case _: ScTemplateBody => false
        case v: ScValueOrVariableDefinition =>
          val isField = v.getParent.is[ScTemplateBody]
          val hasBlockBody = v.expr.exists(_.is[ScBlockExpr])
          val collectAnonymousClassesInside = !(isField && hasBlockBody)
          //Nodes for val/var fields with block bodies will have their own children and for them anonymous classes
          //will be collected in a separate pass (see `getAnonymousClassElements` ScalaDoc)
          if (collectAnonymousClassesInside) {
            v.depthFirst().foreach(process)
          }
          false
        case _: ScMember => false
        case _: ScBlock => false
        case _ => true
      }
      processChildren ||
        (element eq elementToStartFrom) //don't skip children of the node from which we started the traversal
    }.foreach(process)

    result.toSeq
  }
}
