package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings.serialization

import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import org.jetbrains.plugins.scala.lang.formatting.automatic.rule.ScalaFormattingRule
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.ScalaFormattingRuleMatcher
import org.jdom.Element
import scala.collection.JavaConversions._
import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.formatting.Indent
import java.nio.file._
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.statistics.FormattingStatistics
import java.nio.file.attribute.BasicFileAttributes
import com.intellij.openapi.util.io.FileUtil
import scala.Some
import java.io.{FileWriter, BufferedWriter}
import org.jdom.output.XMLOutputter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiManager
import com.intellij.openapi.project.Project

/**
 * Created by Roman.Shein on 26.06.2014.
 */
class ExampleBase(val topRuleToExampleParentBlock: Map[ScalaFormattingRule, List[String]] = Map()) {

  private var ruleToBlocksMap: Option[Map[ScalaFormattingRule, List[ScalaBlock]]] = None

  //TODO: handle caches
  def addMap(otherMap: Map[ScalaFormattingRule, List[String]], examplesPerRule: Int): ExampleBase = {
    if (topRuleToExampleParentBlock.nonEmpty &&
            topRuleToExampleParentBlock.forall(agg => agg._2.length >= examplesPerRule)) return this
    val accMap = mutable.Map(topRuleToExampleParentBlock.toSeq: _*)
    for ((rule, examples) <- otherMap) {
      val curExamples = topRuleToExampleParentBlock.getOrElse(rule, List())
      if (curExamples.length < examplesPerRule) {
        val examplesCut = examples.take(examplesPerRule - curExamples.length)
        accMap.put(rule, examplesCut ::: accMap.getOrElse(rule, List()))
      }
    }
    new ExampleBase(Map(accMap.toSeq:_*))
  }

  def getRuleToBlocksMap(project: Project): Map[ScalaFormattingRule, List[ScalaBlock]] = {
    ruleToBlocksMap match {
      case Some(map) => map
      case None =>
        val res = mutable.Map[ScalaFormattingRule, List[ScalaBlock]]()
        val codeStyleSettings: CodeStyleSettings = new CodeStyleSettings
        for ((rule, examples) <- topRuleToExampleParentBlock) {
          for (example <- examples) {
            val astNode = ScalaPsiElementFactory.createScalaFile(example, PsiManager.getInstance(project)).getNode
            val block = new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent, null, codeStyleSettings)
            res.put(rule, block :: res.getOrElse(rule, List()))
          }
        }
        ruleToBlocksMap = Some(Map(res.toSeq: _*))
        ruleToBlocksMap.get
    }
  }

  def store: Element = {
    val resElement = new Element(ExampleBase.topId)
    for ((rule, blocks) <- topRuleToExampleParentBlock) {
      ExampleBase.storeRuleData(rule, blocks, resElement)
    }
    resElement
  }

}

object ExampleBase {

  private val topId = "SCALA_FORMATTING_EXAMPLE_CODE_BASE"
  private val ruleId = "RULE"
  private val ruleIdId = "ID"
  private val codeExampleId = "code_example"

  def load(from: Element): ExampleBase = {
    val rules = ScalaFormattingRuleMatcher.topRulesByIds
    assert(from.getName == topId)
    val examplesMap = mutable.Map[ScalaFormattingRule, List[String]]()
    for (child <- from.getChildren.toList) {
      assert(child.getName == ruleId)
      val id = child.getChild(ruleIdId).getText
      rules.get(id) match {
        case Some(rule) =>
          loadRuleData(rule, child, examplesMap)
        case None => //the rule is unknown, skip it
      }
    }
    new ExampleBase(Map(examplesMap.toSeq: _*))
  }

  private def extractExample(block: ScalaBlock): Option[String] = block.getFirstNewlineAncestor.map(_.getNode.getText)

  private def storeRuleData(rule: ScalaFormattingRule, examples: List[String], root: Element) {
    val ruleElement = new Element(ruleId)
    ruleElement.addContent(new Element(ruleIdId).setText(rule.id))
    for (example <- examples) {
      ruleElement.addContent(new Element(codeExampleId).setText(example))
    }
    root.addContent(ruleElement)
  }

  private def loadRuleData(rule: ScalaFormattingRule, ruleElement: Element, map: mutable.Map[ScalaFormattingRule, List[String]]) {
    var examples = List[String]()
    for (exampleElement <- ruleElement.getChildren(codeExampleId)) {
      examples = exampleElement.getText :: examples
    }
    map.put(rule, examples ::: map.getOrElse(rule, List()))
  }

  private def build(topBlock: ScalaBlock,
                    matcher: ScalaFormattingRuleMatcher,
                    accBase: ExampleBase,
                    examplesPerRule: Int): ExampleBase = {
    val ruleToParentBlock = mutable.Map[ScalaFormattingRule, List[ScalaBlock]]()
    matcher.matchBlockTree(topBlock, Some(ruleToParentBlock))
    accBase.addMap(Map(
      ruleToParentBlock.map(arg => (arg._1, arg._2.map(extractExample).filter(_.isDefined).map(_.get))).toSeq: _*
    ), examplesPerRule)
  }

  def build(path: Path, examplesPerRule: Int = 100, project: Project): ExampleBase = {

    val codeStyleSettings: CodeStyleSettings = new CodeStyleSettings

    def getRoot(code: String) = {
      val astNode = ScalaPsiElementFactory.createScalaFile(code, PsiManager.getInstance(project)).getNode
      new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent, null, codeStyleSettings)
    }


    var res = new ExampleBase()
    val rootNameCount = path.getNameCount
    Files.walkFileTree(path,
      new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (file.toFile.getName.endsWith(".scala")) {
            val code = new String(FileUtil.loadFileText(file.toFile, "UTF-8"))
            try {
              val codeRoot =  getRoot(code)
              res = build(codeRoot, ScalaFormattingRuleMatcher.getDefaultMatcher(), res, examplesPerRule)
            } catch {
              case e: Throwable =>
                e.printStackTrace
            }
          }
          FileVisitResult.CONTINUE
        }
      }
    )

    res
  }

  def buildAndStore(projectsPath: Path, storePath: Path, examplesPerRule: Int = 100, project: Project) {
    val base = build(projectsPath, examplesPerRule, project)
    val writer = new BufferedWriter(new FileWriter(storePath.toFile))
    val serialized: Element = base.store
    val outputter = new XMLOutputter
    outputter.output(serialized, writer)
  }
}
