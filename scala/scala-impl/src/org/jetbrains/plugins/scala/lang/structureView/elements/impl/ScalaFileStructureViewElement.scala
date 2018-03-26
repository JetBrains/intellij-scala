package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaFileStructureViewElement.Presentation

import scala.collection._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
class ScalaFileStructureViewElement(file: ScalaFile, console: Option[ScalaLanguageConsole] = None)
  extends ScalaStructureViewElement(file, inherited = false) {

  override def getPresentation: ItemPresentation = new Presentation(findRightFile())

  override def getChildren: Array[TreeElement] = {
    val children = new mutable.ArrayBuffer[ScalaStructureViewElement[_]]
    for (child <- findRightFile().getChildren) {
      child match {
        case td: ScTypeDefinition =>
          children += new ScalaTypeDefinitionStructureViewElement(td)
        case packaging: ScPackaging =>
          def getChildren(pack: ScPackaging): Array[ScalaStructureViewElement[_]] = {
            val children = new mutable.ArrayBuffer[ScalaStructureViewElement[_]]
            for (td <- pack.immediateTypeDefinitions) {
              children += new ScalaTypeDefinitionStructureViewElement(td)
            }
            for (p <- pack.packagings) {
              children ++= getChildren(p)
            }
            children.toArray
          }

          children ++= getChildren(packaging)
        case member: ScVariable =>
          for (f <- member.declaredElements)
            children ++= ScalaVariableStructureViewElement(f, false)
        case member: ScValue =>
          for (f <- member.declaredElements)
            children ++= ScalaValueStructureViewElement(f, false)
        case member: ScTypeAlias =>
          children += new ScalaTypeAliasStructureViewElement(member, false)
        case func: ScFunction =>
          children ++= ScalaFunctionStructureViewElement(func, false)
        case block: ScBlockExpr =>
          children += new ScalaBlockStructureViewElement(block)
        case _ =>
      }
    }
    children.toArray
  }

  private def findRightFile(): ScalaFile =
    console.map(_.getHistory)
      .map(history => createScalaFileFromText(s"$history${file.getText}")(file.getManager))
      .getOrElse(file)
}

protected object ScalaFileStructureViewElement {
  class Presentation(element: ScalaFile) extends ScalaItemPresentation(element) {
    override def getPresentableText: String = ScalaElementPresentation.getFilePresentableText(element)
  }
}