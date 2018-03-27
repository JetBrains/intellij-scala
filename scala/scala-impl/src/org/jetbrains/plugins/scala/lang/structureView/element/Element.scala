package org.jetbrains.plugins.scala.lang.structureView.element

import java.util.Objects

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.collection.Seq

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

abstract class Element[T <: PsiElement](val element: T, val inherited: Boolean = false)
  extends StructureViewTreeElement with Presentation {

  override def getPresentation: ItemPresentation = this

  override def getValue: AnyRef = if (element.isValid) element else null

  override def navigate(b: Boolean): Unit = navigatable.foreach(_.navigate(b))

  override def canNavigate: Boolean = navigatable.exists(_.canNavigate)

  override def canNavigateToSource: Boolean = navigatable.exists(_.canNavigateToSource)

  private def navigatable = element.asOptionOf[Navigatable]

  override def getChildren: Array[TreeElement] = TreeElement.EMPTY_ARRAY

  // TODO
  override def equals(o: Any): Boolean = {
    val clazz = o match {
      case obj: Object => obj.getClass
      case _ => return false
    }
    if (o == null || getClass != clazz) return false
    val that = o.asInstanceOf[Element[_]]
    if (inherited != that.inherited) return false

    val value = getValue
    if (value == null) that.getValue == null
    else value == that.getValue
  }

  override def hashCode(): Int = Objects.hash(getValue, Boolean.box(inherited))

  // TODO
  def isAlwaysLeaf: Boolean =
    !(isAlwaysShowsPlus ||
      this.isInstanceOf[Test] ||
      this.isInstanceOf[Block] ||
      this.isInstanceOf[Variable] ||
      this.isInstanceOf[Value] ||
      this.isInstanceOf[Function])

  // TODO
  def isAlwaysShowsPlus: Boolean = {
    this match {
      case _: TypeDefinition => true
      case _: File => true
      case _: Packaging => true
      case _ => false
    }
  }
}

object Element {
  def apply(element: PsiElement, inherited: Boolean = false): Seq[Element[_]] = element match {
    case packaging: ScPackaging => packaging.typeDefinitions.map(new TypeDefinition(_))
    // TODO Type definition can be inherited
    case definition: ScTypeDefinition => Seq(new TypeDefinition(definition))
    case parameter: ScClassParameter => Seq(new ValOrVarParameter(parameter, inherited))
    case function: ScFunction => Seq(//new ScalaFunctionStructureViewElement(function, isInherited, showType = true),
      new Function(function, inherited, showType = false))
    case variable: ScVariable => variable.declaredElements.flatMap( element =>
      Seq(//new ScalaVariableStructureViewElement(element, inherited, showType = true),
      new Variable(element, inherited, showType = false)))
    case value: ScValue => value.declaredElements.flatMap( element =>
      Seq(//new ScalaValueStructureViewElement(element, inherited, showType = true),
      new Value(element, inherited, showType = false)))
    case alias: ScTypeAlias => Seq(new TypeAlias(alias, inherited))
    case block: ScBlockExpr => Seq(new Block(block))
    case _ => Seq.empty
  }

  def apply(fileProvider: () => ScalaFile): Element[_] =
    new File(fileProvider)
}