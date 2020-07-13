package org.jetbrains.plugins.scala.actions.implicitArguments

import java.util
import java.util.Collections.singletonList

import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.{GRAYED_ATTRIBUTES, REGULAR_ATTRIBUTES, STYLE_WAVED}
import javax.swing.Icon
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.implicitArguments.ImplicitArgumentNodes.resolveResultNode
import org.jetbrains.plugins.scala.extensions.{ContainingClass, PsiElementExt, PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.JavaConverters.asJavaCollectionConverter

private abstract class ImplicitParameterErrorNodeBase(value: ScalaResolveResult) extends ImplicitParametersNodeBase(value) {
  private def errorWave: SimpleTextAttributes = {
    val stripeColor = CodeInsightColors.ERRORS_ATTRIBUTES.getDefaultAttributes.getErrorStripeColor
    new SimpleTextAttributes(STYLE_WAVED, null, stripeColor)
  }

  override protected def nameAttributes: SimpleTextAttributes =
    SimpleTextAttributes.merge(errorWave, super.nameAttributes)

  override protected def typeAttributes: SimpleTextAttributes =
    SimpleTextAttributes.merge(errorWave, super.typeAttributes)
}

private class ImplicitArgumentRegularNode(value: ScalaResolveResult) extends ImplicitParametersNodeBase(value)

private class ImplicitArgumentWithReason(value: ScalaResolveResult, reason: FullInfoResult)
  extends ImplicitParameterErrorNodeBase(value) {

  override def updateImpl(data: PresentationData): Unit = {
    data.setTooltip(presentationTooltip)

    super.updateImpl(data)
  }

  @Nls
  private def presentationTooltip: String = reason match {
    case OkResult                        => ScalaBundle.message("implicit.argument.is.applicable")
    case DivergedImplicitResult          => ScalaBundle.message("implicit.is.diverged")
    case CantInferTypeParameterResult    => ScalaBundle.message("can.t.infer.proper.types.for.type.parameters")
    case ImplicitParameterNotFoundResult => ScalaBundle.message("can.t.find.implicit.argument.for.this.definition")
  }

  override protected def infoPrefix: Option[String] = Some(reasonPrefix)

  private def reasonPrefix: String = reason match {
    case OkResult                        => "Applicable: "
    case DivergedImplicitResult          => "Diverged: "
    case CantInferTypeParameterResult    => "Can't infer type: "
    case ImplicitParameterNotFoundResult => "Candidate: "
  }
}

private class ImplicitParameterProblemNode(value: ScalaResolveResult)
  extends ImplicitParameterErrorNodeBase(value) {

  assert(value.isImplicitParameterProblem)

  override def getChildrenImpl: util.Collection[AbstractTreeNode[_]] = {
    val arguments = ImplicitCollector.probableArgumentsFor(value)
    val nodes: Seq[AbstractTreeNode[_]] = arguments.map {
      case (resolveResult, fullInfo) => new ImplicitArgumentWithReason(resolveResult, fullInfo)
    }
    if (nodes.nonEmpty) nodes.asJavaCollection
    else errorLeafNode(ScalaBundle.message("no.implicits.applicable.by.type"))
  }

  private def errorLeafNode(@Nls errorText: String): util.Collection[AbstractTreeNode[_]] = {
    singletonList(new AbstractTreeNode[String](project, errorText) {
      override def getChildren = new util.ArrayList[AbstractTreeNode[_]]()

      override def update(data: PresentationData): Unit = {
        data.setPresentableText(errorText)
        data.setAttributesKey(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
      }
    })
  }

  override protected def infoPrefix: Option[String] = Some(problemPrefix)

  override protected def infoSuffix: Option[String] = None

  private def problemPrefix: String = {
    if (value.isAmbiguousImplicitParameter)
      s"(Ambiguous) "
    else
      s"(Not found) "
  }
}

private abstract class ImplicitParametersNodeBase(value: ScalaResolveResult)
  extends AbstractPsiBasedNode[ScalaResolveResult](value.element.getProject, value, ViewSettings.DEFAULT) {

  protected val project: Project = value.element.getProject

  override def extractPsiFromValue(): PsiNamedElement = value.getElement

  override def getChildrenImpl: util.Collection[AbstractTreeNode[_]] =
    value.implicitParameters
      .map(resolveResultNode)
      .asJavaCollection

  //don't mess with my presentation!
  override def shouldPostprocess(): Boolean = false

  override def updateImpl(data: PresentationData): Unit = {
    data.setIcon(presentationIcon)

    infoPrefix.foreach(data.addText(_, weakTextAttributes))

    data.addText(elementName, nameAttributes)

    typeSuffix.foreach(data.addText(_, typeAttributes))
    infoSuffix.foreach(data.addText(_, weakTextAttributes))
  }

  protected def weakTextAttributes: SimpleTextAttributes = GRAYED_ATTRIBUTES

  protected def strongTextAttributes: SimpleTextAttributes = REGULAR_ATTRIBUTES

  protected def nameAttributes: SimpleTextAttributes = strongTextAttributes

  protected def typeAttributes: SimpleTextAttributes = weakTextAttributes

  protected def infoPrefix: Option[String] = None

  protected def infoSuffix: Option[String] = ImplicitArgumentNodes.locationString(value)

  private def presentationIcon: Icon = value.element.getIcon(0)

  protected def elementName: String = value.element.name

  protected def typeSuffix: Option[String] = {
    value.implicitSearchState.map(": " + _.presentableTypeText)
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case ref: AnyRef => this eq ref
      case _ => false
    }
  }
}

private object ImplicitArgumentNodes {
  def resolveResultNode(srr: ScalaResolveResult): AbstractTreeNode[_] = {
    if (srr.isImplicitParameterProblem) new ImplicitParameterProblemNode(srr)
    else new ImplicitArgumentRegularNode(srr)
  }

  def locationString(value: ScalaResolveResult): Option[String] = {
    if (value.isImplicitParameterProblem) return None

    val description = value.element.nameContext match {
      case p: ScParameter =>
        p.owner match {
          case f: ScFunctionDefinition => s"parameter of ${f.name}"
          case c: ScPrimaryConstructor => s"parameter of ${c.getClassNameText}"
          case _                       => ""
        }
      case ContainingClass(c) =>
        val className = c.getPresentation.getPresentableText
        c match {
          case _: ScClass => "class " + className
          case _: ScTrait => "trait " + className
          case o: ScObject => if (o.isPackageObject) "package object " + className else "object " + className
          case _ => "anonymous class"
        }
      case m: ScMember if m.isLocal =>
        val owner = m.contexts.take(2).collectFirst {
          case named: ScNamedElement => named.name
          case d: ScDeclaredElementsHolder => d.declaredNames.headOption.getOrElse("")
        }
        owner.map(name => s"body of $name").getOrElse("containing block")
      case _ => ""
    }
    if (description != "") Some("  " + description.parenthesize())
    else None
  }

}