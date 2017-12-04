package org.jetbrains.plugins.scala.testingSupport.test

import java.util.regex.{Pattern, PatternSyntaxException}

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.searches.AllClassesSearch
import org.jdom.Element
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.JavaConverters._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind

class RegexpTestData(override val config: AbstractTestRunConfiguration) extends TestConfigurationData(config) {
  private var classRegexps: Array[String] = Array.empty
  private var testRegexps: Array[String] = Array.empty
  def getClassRegexps: Array[String] = classRegexps
  def getTestRegexps: Array[String] = testRegexps
  private var testsBuf: Map[String, Set[String]] = Map()

  protected[test] def zippedRegexps: Array[(String, String)] = classRegexps.zipAll(testRegexps, "", "")

  private def checkRegexps(compileException: (PatternSyntaxException, String) => Exception, noPatternException: Exception): Unit = {
    val patterns = zippedRegexps
    if (patterns.isEmpty) throw noPatternException
    for ((classString, testString) <- patterns) {
      try {
        Pattern.compile(classString)
      } catch {
        case e: PatternSyntaxException => throw compileException(e, classString)
      }
      try {
        Pattern.compile(testString)
      } catch {
        case e: PatternSyntaxException => throw compileException(e, classString)
      }
    }
  }

  private def findTestsByFqnCondition(classCondition: String => Boolean, testCondition: String => Boolean,
                                      classToTests: mutable.Map[String, Set[String]]): Unit = {
    val suiteClasses = AllClassesSearch
      .search(config.getSearchScope.intersectWith(GlobalSearchScopesCore.projectTestScope(getProject)), getProject)
      .asScala
      .filter(c => classCondition(c.qualifiedName)).filterNot(config.isInvalidSuite)

    //we don't care about linearization here, so can process in arbitrary order
    @tailrec
    def getTestNames(classesToVisit: List[ScTypeDefinition], visited: Set[ScTypeDefinition] = Set.empty,
                     res: Set[String] = Set.empty): Set[String] = {
      if (classesToVisit.isEmpty) res
      else if (visited.contains(classesToVisit.head)) getTestNames(classesToVisit.tail, visited, res)
      else {
        getTestNames(classesToVisit.head.supers.toList.filter(_.isInstanceOf[ScTypeDefinition]).
          map(_.asInstanceOf[ScTypeDefinition]).filter(!visited.contains(_)) ++ classesToVisit.tail,
          visited + classesToVisit.head,
          res ++ TestNodeProvider.getTestNames(classesToVisit.head, config.configurationProducer))
      }
    }

    suiteClasses.map {
      case aSuite: ScTypeDefinition =>
        val tests = getTestNames(List(aSuite))
        classToTests += (aSuite.qualifiedName -> tests.filter(testCondition))
      case _ => None
    }
  }

  override def checkSuiteAndTestName(): Unit = {
    checkModule()
    checkRegexps((_, p) => new RuntimeConfigurationException(s"Failed to compile pattern $p"), new RuntimeConfigurationException("No patterns detected"))
  }

  override def getTestMap(): Map[String, Set[String]] = {
    val patterns = zippedRegexps
    val classToTests = mutable.Map[String, Set[String]]()
    if (isDumb) {
      if (testsBuf.isEmpty) throw new ExecutionException("Can't run while indexing: no class names memorized from previous iterations.")
      return testsBuf
    }

    def getCondition(patternString: String): String => Boolean = {
      try {
        val pattern = Pattern.compile(patternString)
        (input: String) =>
          input != null && (pattern.matcher(input).matches ||
            input.startsWith("_root_.") && pattern.matcher(input.substring("_root_.".length)).matches)
      } catch {
        case e: PatternSyntaxException =>
          throw new ExecutionException(s"Failed to compile pattern $patternString", e)
      }
    }

    patterns foreach {
      case ("", "") => //do nothing, empty patterns are ignored
      case ("", testPatternString) => //run all tests with names matching the pattern
        findTestsByFqnCondition(_ => true, getCondition(testPatternString), classToTests)
      case (classPatternString, "") => //run all tests for all classes matching the pattern
        findTestsByFqnCondition(getCondition(classPatternString), _ => true, classToTests)
      case (classPatternString, testPatternString) => //the usual case
        findTestsByFqnCondition(getCondition(classPatternString), getCondition(testPatternString), classToTests)
    }
    val res = classToTests.toMap.filter(_._2.nonEmpty)
    testsBuf = res
    res
  }

  override def readExternal(element: Element): Unit = {
    def loadRegexps(pMap: mutable.Map[String, String]): Array[String] = {
      val res = new Array[String](pMap.size)
      pMap.foreach { case (index, pattern) => res(Integer.parseInt(index)) = pattern }
      res
    }

    val classRegexpsMap = mutable.Map[String, String]()
    JDOMExternalizer.readMap(element, classRegexpsMap.asJava, "classRegexps", "pattern")
    classRegexps = loadRegexps(classRegexpsMap)
    val testRegexpssMap = mutable.Map[String, String]()
    JDOMExternalizer.readMap(element, testRegexpssMap.asJava, "testRegexps", "pattern")
    testRegexps = loadRegexps(testRegexpssMap)
    testsBuf = readBufMap(element)
  }

  override def writeExternal(element: Element): Unit = {
    val classRegexps: Map[String, String] = Map(zippedRegexps.sorted.zipWithIndex.map { case ((c, _), i) => (i.toString, c) }: _*)
    val testRegexps: Map[String, String] = Map(zippedRegexps.sorted.zipWithIndex.map { case ((_, t), i) => (i.toString, t) }: _*)
    JDOMExternalizer.writeMap(element, classRegexps.asJava, "classRegexps", "pattern")
    JDOMExternalizer.writeMap(element, testRegexps.asJava, "testRegexps", "pattern")
    writeBufMap(element)
  }

  private def readBufMap(element: Element): Map[String, Set[String]] = {
    val buf = element.getChild("buffered")
    if (buf == null) return Map()
    Map(buf.getChildren("entry").asScala.map{ entry =>
      entry.getAttributeValue("class") -> entry.getChildren("test").asScala.map(_.getAttributeValue("name")).toSet
    }:_*)
  }

  private def writeBufMap(element: Element): Unit = {
    val buf = new Element("buffered")
    testsBuf.toSeq.sortBy(_._1).foreach { case (className, tests) =>
      val classElement = new Element("entry")
      classElement.setAttribute("class", className)
      tests.toSeq.sorted.foreach { testName =>
        val testElement = new Element("test")
        testElement.setAttribute("name", testName)
        classElement.addContent(testElement)
      }
      buf.addContent(classElement)
    }
    element.addContent(buf)
  }

  override def getKind: TestKind = TestKind.REGEXP

  override def apply(form: TestRunConfigurationForm): Unit = {
    classRegexps = form.getClassRegexps
    testRegexps = form.getTestRegexps
  }
}

object RegexpTestData {
  def apply(config: AbstractTestRunConfiguration, classRegexps: Array[String], testRegexps: Array[String]): RegexpTestData = {
    val res = new RegexpTestData(config)
    res.classRegexps = classRegexps
    res.testRegexps = testRegexps
    res
  }
}
