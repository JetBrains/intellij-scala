package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaFileStructureViewElement.Presentation

import scala.collection._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
class ScalaFileStructureViewElement(file: ScalaFile, console: Option[ScalaLanguageConsole] = None)
  extends ScalaStructureViewElement(file, inherited = false) {

  override def getPresentation: ItemPresentation = new Presentation(fileWithConsoleHistory)

  override def getChildren: Array[TreeElement] = {
    val children = fileWithConsoleHistory.getChildren.toSeq

    val result = children.flatMap {
      case block: ScBlockExpr => Seq(new ScalaBlockStructureViewElement(block))
      // TODO Test packagings
      case packaging: ScPackaging => packaging.typeDefinitions.map(new ScalaTypeDefinitionStructureViewElement(_))
      // TODO Type definition can be inherited
      case definition: ScTypeDefinition => Seq(new ScalaTypeDefinitionStructureViewElement(definition))
      case function: ScFunction => ScalaFunctionStructureViewElement(function, inherited = false)
      case variable: ScVariable => variable.declaredElements.flatMap(ScalaVariableStructureViewElement(_, inherited = false))
      case value: ScValue => value.declaredElements.flatMap(ScalaValueStructureViewElement(_, inherited = false))
      case alias: ScTypeAlias => Seq(new ScalaTypeAliasStructureViewElement(alias, inherited = false))
      case _ => Seq.empty
    }

    result.toArray
  }

  // TODO Improve this
  private def fileWithConsoleHistory: ScalaFile =
    console.map(_.getHistory)
      .map(history => createScalaFileFromText(s"$history${file.getText}")(file.getManager))
      .getOrElse(file)
}

private object ScalaFileStructureViewElement {
  class Presentation(file: ScalaFile) extends ScalaItemPresentation(file) {
    override def getPresentableText: String = file.name
  }
}