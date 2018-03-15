package org.jetbrains.plugins.scala.lang.macros.expansion

import java.io._

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugin.scala.util.{MacroExpansion, Place}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation

import scala.collection.mutable
import org.jetbrains.plugins.scala

/**
  * @author Mikhail Mutcianko
  * @since 20.09.16
  */
class ReflectExpansionsCollector(project: Project) extends AbstractProjectComponent(project) {
  override def getComponentName = "ReflectExpansionsCollector"

  private val collectedExpansions: mutable.HashMap[Place, MacroExpansion] = mutable.HashMap.empty
  private var parser: ScalaReflectMacroExpansionParser = _


  override def projectOpened(): Unit = {
    deserializeExpansions()
  }

  def getExpansion(elem: PsiElement): Option[MacroExpansion] = {
    val offset = PsiTreeUtil.getParentOfType(elem, classOf[ScAnnotation]) match {
      case _: ScAnnotation => elem.getTextOffset
      case _ => elem.getNode.getTextRange.getEndOffset
    }
    val path = elem.getContainingFile.getVirtualFile.getPath
    val place = Place(path, offset)()
    collectedExpansions.get(place)
  }

  def processCompilerMessage(text: String): Unit = {
    parser.processMessage(text)
  }

  def compilationStarted(): Unit = {
    collectedExpansions.clear()
    parser = new ScalaReflectMacroExpansionParser(project.getName)
  }

  def compilationFinished(): Unit = {
    parser.expansions.foreach { exp => collectedExpansions += exp.place -> exp }
    serializeExpansions()
  }

  private def deserializeExpansions(): Unit = {
    val file = new File(System.getProperty("java.io.tmpdir") + s"/expansion-${project.getName}")

    if (!file.exists())
      return

    extensions.using(new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) { os =>
      collectedExpansions ++= os.readObject().asInstanceOf[collectedExpansions.type]
    }
  }

  def serializeExpansions(): Unit = {
    val file = new File(System.getProperty("java.io.tmpdir") + s"/expansion-${project.getName}")
    scala.extensions.using(new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
      _.writeObject(collectedExpansions)
    }
  }

}

object ReflectExpansionsCollector {
  def getInstance(project: Project): ReflectExpansionsCollector = project.getComponent(classOf[ReflectExpansionsCollector])
}
