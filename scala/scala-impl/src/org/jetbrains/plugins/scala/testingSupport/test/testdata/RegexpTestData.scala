package org.jetbrains.plugins.scala.testingSupport.test.testdata

import java.util.regex.{Pattern, PatternSyntaxException}
import java.{util => ju}

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

import scala.annotation.tailrec
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._
import scala.collection.mutable

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

  private def findTestsByFqnCondition(classCondition: String => Boolean, testCondition: String => Boolean,
                                      classToTests: mutable.Map[String, Set[String]]): Unit = {
    val suiteClasses = AllClassesSearch
      .search(config.getSearchScope.intersectWith(GlobalSearchScopesCore.projectTestScope(getProject)), getProject)
      .asScala
      .filter(c => classCondition(c.qualifiedName)).filter(config.isValidSuite)

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
    val classToTests = mutable.Map[String, Set[String]]()
    if (isDumb) {
      if (testsBuf.isEmpty) throw executionException(ScalaBundle.message("test.config.cant.run.while.indexing.no.class.names.memorized.from.previous.iterations"))
      return testsBuf.asScala.map { case (k,v) => k -> v.asScala.toSet }.toMap
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
    testsBuf = res.map { case (k, v) => k -> v.asJava }.asJava
    res
  }

  override def apply(form: TestRunConfigurationForm): Unit = {
    super.apply(form)
    val regexps = form.getRegexps
    classRegexps = regexps._1
    testRegexps = regexps._2
  }

  override protected def apply(data: RegexpTestData): Unit = {
    super.apply(data)
    data.testsBuf = new ju.HashMap(testsBuf.size())
    testsBuf.asScala.foreach { case (k, v) => data.testsBuf.put(k, new ju.HashSet[String](v)) }
  }

  override def copy(config: AbstractTestRunConfiguration): RegexpTestData = {
    val data = new RegexpTestData(config)
    data.apply(this)
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
