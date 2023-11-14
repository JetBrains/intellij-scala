package org.jetbrains.plugins.scala.structureView

import com.intellij.ide.structureView.{StructureViewModel, StructureViewTreeElement, TextEditorBasedStructureViewModel}
import com.intellij.ide.util.treeView.smartTree.*
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.structureView.element.Element
import org.jetbrains.plugins.scala.structureView.filter.ScalaPublicElementsFilter
import org.jetbrains.plugins.scala.structureView.grouper.ScalaSuperTypesGrouper
import org.jetbrains.plugins.scala.structureView.sorter.{ScalaAlphaSorter, ScalaByPositionSorter, ScalaVisibilitySorter}

import java.util
import scala.jdk.CollectionConverters.*

class ScalaStructureViewModel(myRootElement: ScalaFile, console: Option[ScalaLanguageConsole] = None)
  extends TextEditorBasedStructureViewModel(myRootElement) with StructureViewModel.ElementInfoProvider {

  override def isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
    element.asOptionOf[Element].exists(_.isAlwaysShowsPlus)

  override def isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
    element.asOptionOf[Element].forall(_.isAlwaysLeaf)

  override def getRoot: StructureViewTreeElement = {
    def file = console.map(_.getHistory)
      .map(history => createScalaFileFromText(s"$history${myRootElement.getText}", myRootElement)(myRootElement.getManager))
      .getOrElse(myRootElement)

    Element(() => file)
  }

// TODO Enable inferred types
//  override def getFilters: Array[Filter] = Array(new Filter {
//    override def getName: String = "INFERRED_TYPES"
//
//    override def getPresentation: ActionPresentation =
//      new ActionPresentationData("Inferred Types", "Show inferred types", Icons.TYPED)
//
//    override def isVisible(element: TreeElement): Boolean = element match {
//      case e: TypedViewElement => e.showType
//      case _ => true
//    }
//
//    override def isReverted: Boolean = false
//  })

  override def getFilters: Array[Filter] = {
    val filters = Array[Filter](
      ScalaPublicElementsFilter
    )

    filters
  }

  override def getGroupers: Array[Grouper] = {
    val groupers = Array[Grouper](
      ScalaSuperTypesGrouper
    )

    groupers
  }

  override def getSorters: Array[Sorter] = {
    val sorters = Array(
      ScalaVisibilitySorter,
      ScalaAlphaSorter,
      ScalaByPositionSorter,
    )

    sorters
  }

  override def getNodeProviders: util.Collection[NodeProvider[_ <: TreeElement]] = (
    new ScalaInheritedMembersNodeProvider() +:
      ScalaStructureViewModelProvider.nodeProvidersFor(myRootElement) :+
      new ScalaAnonymousClassesNodeProvider()
    ).asJava

  override def isSuitable(element: PsiElement): Boolean = element match {
    case t: ScTypeDefinition => t.getParent match {
      case _: ScalaFile | _: ScPackaging => true
      case tb: ScTemplateBody if tb.getParent.is[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case f: ScFunction => f.getParent match {
      case b: ScBlockExpr => b.getParent.is[ScFunction]
      case tb: ScTemplateBody if tb.getParent.is[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case m: ScMember => m.getParent match {
      case tb: ScTemplateBody if tb.getParent.is[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case _ => false
  }

  override def shouldEnterElement(o: Object): Boolean = o match {
    case t : ScTypeDefinition => t.membersWithSynthetic.nonEmpty || t.typeDefinitions.nonEmpty
    case _ => false
  }
}
