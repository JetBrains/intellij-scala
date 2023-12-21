package org.jetbrains.plugins.scala.structureView

import com.intellij.ide.structureView.{StructureViewModel, StructureViewTreeElement, TextEditorBasedStructureViewModel}
import com.intellij.ide.util.treeView.smartTree.*
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExtension, ScExtensionBody, ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.structureView.element.Element
import org.jetbrains.plugins.scala.structureView.filter.ScalaPublicElementsFilter
import org.jetbrains.plugins.scala.structureView.grouper.ScalaSuperTypesGrouper
import org.jetbrains.plugins.scala.structureView.sorter.{ScalaAlphaSorter, ScalaByPositionSorter, ScalaVisibilitySorter}

import java.util
import scala.annotation.tailrec
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

  @tailrec
  final override def isSuitable(element: PsiElement): Boolean = element match {
    case enumCase: ScEnumCase =>
      isSuitable(enumCase.enumParent)
    case classParam: ScClassParameter =>
      isSuitable(classParam.containingClass)
    case (named: ScTypedDefinition) & (_: ScFieldId | _: ScBindingPattern) =>
      named.nameContext match
        case variable: ScValueOrVariable =>
          isSuitable(variable)
        case _ => false
    case function: ScFunction =>
      function.getParent match
        case (_: ScBlockExpr) & Parent(_: ScFunction) => true
        case (_: ScExtensionBody) & Parent(ext: ScExtension) =>
          isSuitable(ext)
        case _ => isToplevelOrInsideSuitableTypeDef(function)
    case member: ScMember =>
      isToplevelOrInsideSuitableTypeDef(member)
    case _ => false
  }

  override def shouldEnterElement(o: Object): Boolean = o match {
    case t: ScTypeDefinition => t.membersWithSynthetic.nonEmpty || t.typeDefinitions.nonEmpty
    case _ => false
  }

  @tailrec
  private def isToplevelOrInsideSuitableTypeDef(member: ScMember): Boolean =
    member.getParent match
      case _: ScalaFile | _: ScPackaging => true
      case (_: ScTemplateBody) & Parent((_: ScExtendsBlock) & Parent(td: ScTypeDefinition)) =>
        isToplevelOrInsideSuitableTypeDef(td)
      case _ => false
}
