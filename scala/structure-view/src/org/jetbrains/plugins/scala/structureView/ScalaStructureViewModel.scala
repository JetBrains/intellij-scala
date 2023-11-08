package org.jetbrains.plugins.scala.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.{StructureViewModel, StructureViewTreeElement, TextEditorBasedStructureViewModel}
import com.intellij.ide.util.treeView.smartTree.*
import com.intellij.openapi.editor.PlatformEditorBundle
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
import org.jetbrains.plugins.scala.structureView.element.{Element, Test}

import java.util
import java.util.Comparator
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

  override def getSorters: Array[Sorter] = {
    val sorters = Array(
      ScalaVisibilitySorter,
      new Sorter() {
        override def isVisible: Boolean = true

        // TODO move to the implementation of testing support
        override def getComparator: Comparator[_] =
          (o1: AnyRef, o2: AnyRef) => (o1, o2) match {
            case (_: Test, _: Test) => 0
            case (_, _: Test) => -1
            case (_: Test, _) => 1
            case _ => SorterUtil.getStringPresentation(o1).compareToIgnoreCase(SorterUtil.getStringPresentation(o2))
          }

        override def getName: String = "ALPHA_SORTER_IGNORING_TEST_NODES"

        override def getPresentation: ActionPresentation = {
          val sortAlphabetically = PlatformEditorBundle.message("action.sort.alphabetically")
          new ActionPresentationData(
            sortAlphabetically,
            sortAlphabetically,
            AllIcons.ObjectBrowser.Sorted
          )
        }
      },
      new Sorter() {
        override def isVisible: Boolean = false

        override def getName: String = "ACTUAL_ORDER_SORTER"

        override def getPresentation: ActionPresentation =
          new ActionPresentationData("Sort.actually", "Sort By Position", AllIcons.ObjectBrowser.Sorted)

        override def getComparator: Comparator[_] = (o1: AnyRef, o2: AnyRef) => (o1, o2) match {
          case (e1: Element, e2: Element) if !e1.inherited && !e2.inherited =>
            e1.element.getTextOffset - e2.element.getTextOffset
          case _ => 0
        }
      }
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
