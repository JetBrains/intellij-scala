package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createScalaFileFromText
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

import scala.collection._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
class ScalaFileStructureViewElement(file: ScalaFile, private val console: ScalaLanguageConsole = null)
  extends ScalaStructureViewElement(file, false) {
  def getPresentation: ItemPresentation = {
    new ScalaFileItemPresentation(findRightFile())
  }

  def getChildren: Array[TreeElement] = {
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
            children += new ScalaVariableStructureViewElement(f, false)
        case member: ScValue =>
          for (f <- member.declaredElements)
            children += new ScalaValueStructureViewElement(f, false)
        case member: ScTypeAlias =>
          children += new ScalaTypeAliasStructureViewElement(member, false)
        case func: ScFunction =>
          children += new ScalaFunctionStructureViewElement(func, false)
        case _ =>
      }
    }
    children.toArray
  }

  private def findRightFile(): ScalaFile = Option(console).map {
    _.getHistory
  }.map { history =>
    createScalaFileFromText(s"$history${file.getText}")(file.getManager)
  }.getOrElse(file)
}