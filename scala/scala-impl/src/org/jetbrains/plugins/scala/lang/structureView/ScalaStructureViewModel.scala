package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.{StructureViewModel, StructureViewTreeElement, TextEditorBasedStructureViewModel}
import com.intellij.ide.util.treeView.smartTree._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.structureView.element.{Element, Test}
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

import java.util
import java.util.Comparator

class ScalaStructureViewModel(myRootElement: ScalaFile, console: Option[ScalaLanguageConsole] = None)
  extends TextEditorBasedStructureViewModel(myRootElement) with StructureViewModel.ElementInfoProvider {

  override def isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
    element.asOptionOf[Element].exists(_.isAlwaysShowsPlus)

  override def isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
    element.asOptionOf[Element].forall(_.isAlwaysLeaf)

  override def getRoot: StructureViewTreeElement = {
    def file = console.map(_.getHistory)
      .map(history => createScalaFileFromText(s"$history${myRootElement.getText}")(myRootElement.getManager))
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

  override def getSorters: Array[Sorter] = {
    val res = new Array[Sorter](2)
    res(0) = new Sorter() {
      override def isVisible: Boolean = true

      // TODO move to the implemenation of testing support
      override def getComparator: Comparator[_] =
        (o1: AnyRef, o2: AnyRef) => (o1, o2) match {
          case (_: Test, _: Test) => 0
          case (_, _: Test) => -1
          case (_: Test, _) => 1
          case _ => SorterUtil.getStringPresentation(o1).compareToIgnoreCase(SorterUtil.getStringPresentation(o2))
        }

      override def getName: String = "ALPHA_SORTER_IGNORING_TEST_NODES"

      override def getPresentation: ActionPresentation = new ActionPresentationData(
        IdeBundle.message("action.sort.alphabetically"),
        IdeBundle.message("action.sort.alphabetically"),
        AllIcons.ObjectBrowser.Sorted
      )
    }
    res(1) = new Sorter() {
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
    res
  }

  override def getNodeProviders: util.Collection[NodeProvider[_ <: TreeElement]] = {
    if (myRootElement.getFileType == ScalaFileType.INSTANCE)
      util.Arrays.asList(new ScalaInheritedMembersNodeProvider, new TestNodeProvider)
    else
      util.Arrays.asList(new ScalaInheritedMembersNodeProvider)
  }

  override def isSuitable(element: PsiElement): Boolean = element match {
    case t: ScTypeDefinition => t.getParent match {
      case _: ScalaFile | _: ScPackaging => true
      case tb: ScTemplateBody if tb.getParent.isInstanceOf[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case f: ScFunction => f.getParent match {
      case b: ScBlockExpr => b.getParent.isInstanceOf[ScFunction]
      case tb: ScTemplateBody if tb.getParent.isInstanceOf[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case m: ScMember => m.getParent match {
      case tb: ScTemplateBody if tb.getParent.isInstanceOf[ScExtendsBlock] =>
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