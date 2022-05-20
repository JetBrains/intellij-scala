package org.jetbrains.plugins.scala.testingSupport.test.testdata

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.searches.AllClassesSearch
import org.jdom.Element
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestKind}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import java.util.regex.{Pattern, PatternSyntaxException}
import java.{util => ju}
import scala.annotation.tailrec
import scala.beans.BeanProperty
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class RegexpTestData(config: AbstractTestRunConfiguration) extends TestConfigurationData(config) {

  override type SelfType = RegexpTestData

  override def getKind: TestKind = TestKind.REGEXP

  @BeanProperty var classRegexps: Array[String] = Array.empty
  @BeanProperty var testRegexps: Array[String]  = Array.empty
  @BeanProperty var testsBuf: ju.Map[String, ju.Set[String]] = new ju.HashMap()

  protected[test] def zippedRegexps: Array[(String, String)] = classRegexps.zipAll(testRegexps, "", "")
  def regexps: (Array[String], Array[String]) = (classRegexps, testRegexps)

  private def checkRegexps(compileException: (PatternSyntaxException, String) => RuntimeConfigurationException,
                           noPatternException: RuntimeConfigurationException): CheckResult = {
    val patterns = zippedRegexps
    if (patterns.isEmpty) return Left(noPatternException)

    for ((classString, testString) <- patterns) {
      try {
        Pattern.compile(classString)
      } catch {
        case e: PatternSyntaxException =>
          return Left(compileException(e, classString))
      }
      try {
        Pattern.compile(testString)
      } catch {
        case e: PatternSyntaxException =>
          return Left(compileException(e, testString))
      }
    }
    Right(())
  }

  private def findTestsByFqnCondition(
    classCondition: String => Boolean,
    testCondition: String => Boolean,
    outputClassToTests: mutable.Map[String, Set[String]]
  ): Unit = {
    val suiteClasses = findTestClassesByFqnCondition(classCondition)
    suiteClasses.foreach { testClass =>
      val className = testClass.qualifiedName
      val testNames = findTestNamesByFqnCondition(testClass, testCondition)
      outputClassToTests += (className -> testNames)
    }
  }

  private def findTestClassesByFqnCondition(classCondition: String => Boolean): Iterable[ScTypeDefinition] = {
    val scope = config.getSearchScope.intersectWith(GlobalSearchScopesCore.projectTestScope(getProject))
    val classes = AllClassesSearch.search(scope, getProject).asScala
    classes
      .filter(c => classCondition(c.qualifiedName))
      .filter(config.isValidSuite)
      .filterByType[ScTypeDefinition]
  }

  private def findTestNamesByFqnCondition(
    testClass: ScTypeDefinition,
    testCondition: String => Boolean
  ): Set[String] = {
    val tests = collectTestNames(List(testClass))
    tests.filter(testCondition)
  }

  //we don't care about linearization here, so can process in arbitrary order
  @tailrec
  private def collectTestNames(
    classesToVisit: List[ScTypeDefinition],
    visited: Set[ScTypeDefinition] = Set.empty,
    res: Set[String] = Set.empty
  ): Set[String] = {
    if (classesToVisit.isEmpty)
      res
    else if (visited.contains(classesToVisit.head))
      collectTestNames(classesToVisit.tail, visited, res)
    else {
      val (head, tail) = (classesToVisit.head, classesToVisit.tail)

      val notVisitedSupers = head.supers
        .filterByType[ScTypeDefinition]
        .filter(!visited.contains(_)).toList
      val classesToVisitNew = notVisitedSupers ++ tail

      val visitedNew = visited + classesToVisit.head

      // TODO: consider delegating tests filtering by regexp to test framework itself
      //  our approach can't work with dynamically-created tests
      //  the full picture of tests can only be obtained inside test runner itself
      val testNamesNew = TestNodeProvider.getTestNames(classesToVisit.head, config.configurationProducer)
      val resNew = res ++ testNamesNew

      collectTestNames(classesToVisitNew, visitedNew, resNew)
    }
  }

  override def checkSuiteAndTestName: CheckResult =
    for {
      _ <- checkModule
      _ <- checkRegexps(
        (_, p) => configurationException(ScalaBundle.message("test.config.failed.to.compile.pattern", p)),
        configurationException(ScalaBundle.message("test.config.no.patterns.detected"))
      )
    } yield ()

  override def getTestMap: Map[String, Set[String]] = {
    val patterns = zippedRegexps

    if (isDumb) {
      if (testsBuf.isEmpty)
        throw executionException(ScalaBundle.message("test.config.cant.run.while.indexing.no.class.names.memorized.from.previous.iterations"))
      val cached = testsBuf.asScala.map { case (k, v) => k -> v.asScala.toSet }.toMap
      return cached
    }

    def getCondition(patternString: String): String => Boolean = {
      try {
        val pattern = Pattern.compile(patternString)
        (input: String) =>
          input != null && (pattern.matcher(input).matches ||
            input.startsWith("_root_.") && pattern.matcher(input.substring("_root_.".length)).matches)
      } catch {
        case e: PatternSyntaxException =>
          throw executionException(ScalaBundle.message("test.config.failed.to.compile.pattern", patternString), e)
      }
    }

    val classToTests = mutable.Map.empty[String, Set[String]]
    patterns.foreach {
      case ("", "") =>
        Map.empty[String, Set[String]] //do nothing, empty patterns are ignored
      case ("", testPatternString) => //run all tests with names matching the pattern
        findTestsByFqnCondition(_ => true, getCondition(testPatternString), classToTests)
      case (classPatternString, "") => //run all tests for all classes matching the pattern
        findTestsByFqnCondition(getCondition(classPatternString), _ => true, classToTests)
      case (classPatternString, testPatternString) => //the usual case
        findTestsByFqnCondition(getCondition(classPatternString), getCondition(testPatternString), classToTests)
    }
    val res = classToTests.toMap.filter(_._2.nonEmpty)
    testsBuf = res.map { case (k, v) => k -> v.asJava }.asJava
    res
  }

  override def copyFieldsFromForm(form: TestRunConfigurationForm): Unit = {
    super.copyFieldsFromForm(form)
    val regexps = form.getRegexps
    classRegexps = regexps._1
    testRegexps = regexps._2
  }

  override protected def copyFieldsFrom(data: RegexpTestData): Unit = {
    super.copyFieldsFrom(data)
    data.testsBuf = new ju.HashMap(testsBuf.size())
    testsBuf.asScala.foreach { case (k, v) => data.testsBuf.put(k, new ju.HashSet[String](v)) }
  }

  override def copy(config: AbstractTestRunConfiguration): RegexpTestData = {
    val data = new RegexpTestData(config)
    data.copyFieldsFrom(this)
    data
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateArray("classRegexps", "pattern")(arr => classRegexps = arr.clone())
      helper.migrateArray("testRegexps", "pattern")(arr => testRegexps = arr.clone())
    }
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
