package org.jetbrains.plugins.scala.util.runners

import java.lang.annotation.Annotation

import com.intellij.pom.java.{LanguageLevel => JdkVersion}
import junit.extensions.TestDecorator
import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{InjectableJdk, ScalaSdkOwner}
import org.junit.experimental.categories.Category
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.{Describable, Description}

import scala.annotation.tailrec
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, enumerationAsScalaIteratorConverter}

class MultipleScalaVersionsRunner(myTest: Test, klass: Class[_]) extends JUnit38ClassRunner(myTest) {

  def this(klass: Class[_]) =
    this(MultipleScalaVersionsRunner.testSuite(klass.asSubclass(classOf[TestCase])), klass)

  override def getDescription: Description = {
    val description = MultipleScalaVersionsRunner.makeDescription(klass, myTest)
    //debugLog(description)
    description
  }
}

private object MultipleScalaVersionsRunner {

  private val DefaultScalaVersionsToRun: Seq[TestScalaVersion] =
    Seq(
      TestScalaVersion.Scala_2_10,
      TestScalaVersion.Scala_2_11,
      TestScalaVersion.Scala_2_12,
      TestScalaVersion.Scala_2_13,
    )

  private val DefaultJdkVersionToRun: TestJdkVersion =
    TestJdkVersion.from(InjectableJdk.DefaultJdk)

  // use these vm parameters to specific scala/jdk versions of MultipleScalaVersionsRunner
  // without changing RunWith* annotations values
  private def filterScalaVersion = Option(System.getProperty("filter.test.scala.version")).map(TestScalaVersion.valueOf)
  private def filterJdkVersion = Option(System.getProperty("filter.test.jdk.version")).map(TestJdkVersion.valueOf)

  private case class ScalaVersionTestSuite(name: String) extends TestSuite(name) {
    def this() = this(null: String)
    def this(version: ScalaVersion) = this(sanitize(s"(scala ${version.minor})"))
    def this(version: ScalaVersion, jdkVersion: JdkVersion) = this(sanitize(s"(scala ${version.minor} $jdkVersion)"))
  }

  private case class JdkVersionTestSuite(name: String) extends TestSuite(name) {
    def this() = this(null: String)
    def this(version: JdkVersion) = this(sanitize(s"(jdk ${version.toString})"))
  }

  def testSuite(klass: Class[_ <: TestCase]): TestSuite = {
    assert(classOf[ScalaSdkOwner].isAssignableFrom(klass))

    val suite = new TestSuite
    suite.setName(klass.getName)

    val classScalaVersions = scalaVersionsToRun(klass)
    val classJdkVersions = jdkVersionsToRun(klass)
    assert(classScalaVersions.nonEmpty, "at least one scala version should be specified")
    assert(classJdkVersions.nonEmpty, "at least one jdk version should be specified")

    val allTestCases: Seq[(TestCase, ScalaVersion, JdkVersion)] = {
      val collected = new ScalaVersionAwareTestsCollector(klass, classScalaVersions, classJdkVersions).collectTests()
      collected.collect { case (test, sv, jv)
        if filterScalaVersion.forall(_ == sv) && filterJdkVersion.forall(_ == jv) =>
        (test, sv.toProductionVersion, jv.toProductionVersion)
      }
    }

    val childTests = childTestsByScalaVersion(allTestCases)
    // val childTests = childTestsByName(allTests)
    childTests.foreach { childTest =>
      suite.addTest(childTest)
    }

    suite
  }

//  private def childTestsByName(testsCases: Seq[(TestCase, ScalaVersion, JdkVersion)]): Seq[Test] = {
//    val nameToTests: Map[String, Seq[(TestCase, ScalaVersion)]] = testsCases.groupBy(_._1.getName)
//
//    for {
//      (testName, tests: Seq[(TestCase, ScalaVersion)]) <- nameToTests.toSeq.sortBy(_._1)
//    } yield {
//      if (tests.size == 1) tests.head._1
//      else {
//        val suite = new framework.TestSuite()
//        suite.setName(testName)
//        tests.sortBy(_._2).foreach { case (t, version) =>
//          t.setName(testName + "." + sanitize(version.minor))
//          suite.addTest(t)
//        }
//        suite
//      }
//    }
//  }

  private def childTestsByScalaVersion(testCases: Seq[(TestCase, ScalaVersion, JdkVersion)]): Seq[Test] = {
    val scalaVersionToTests: Map[ScalaVersion, Seq[Test]] =
      testCases.groupBy(_._2).mapValues(_.map(t => (t._1, t._3)))
        .mapValues(childTestsByJdkVersion)

    if (scalaVersionToTests.size == 1) {
      scalaVersionToTests.head._2
    } else {
      for {
        (version, tests) <- scalaVersionToTests.toSeq.sortBy(_._1)
        if tests.nonEmpty
      } yield {
        val firstTest = tests.head
        val suite = if (firstTest.isInstanceOf[JdkVersionTestSuite]) {
          new ScalaVersionTestSuite(version)
        } else {
          // if only one jdk version is used, display it in the test name
          val jdkVersion = firstTest.asInstanceOf[ScalaSdkOwner].testProjectJdkVersion
          new ScalaVersionTestSuite(version, jdkVersion)
        }
        tests.foreach(suite.addTest)
        suite
      }
    }
  }

  private def childTestsByJdkVersion(testCases: Seq[(TestCase, JdkVersion)]): Seq[Test] = {
    val jdkVersionToTests: Map[JdkVersion, Seq[TestCase]] =
      testCases.groupBy(_._2).mapValues(_.map(_._1))

    if (jdkVersionToTests.size == 1) jdkVersionToTests.head._2 else {
      for {
        (version, tests) <- jdkVersionToTests.toSeq.sortBy(_._1)
        if tests.nonEmpty
      } yield {
        val suite = new JdkVersionTestSuite(version)
        tests.foreach(suite.addTest)
        suite
      }
    }
  }

  private def scalaVersionsToRun(klass: Class[_ <: TestCase]): Seq[TestScalaVersion] = {
    val annotation = findAnnotation(klass, classOf[RunWithScalaVersions])
    annotation
      .map(_.value.toSeq)
      .getOrElse(DefaultScalaVersionsToRun)
  }

  private def jdkVersionsToRun(klass: Class[_ <: TestCase]): Seq[TestJdkVersion] = {
    val annotation = findAnnotation(klass, classOf[RunWithJdkVersions])
    annotation
      .map(_.value.toSeq)
      .getOrElse(Seq(DefaultJdkVersionToRun))
  }

  private def findAnnotation[T <: Annotation](klass: Class[_], annotationClass: Class[T]): Option[T] = {
    @tailrec
    def inner(c: Class[_]): Annotation = c.getAnnotation(annotationClass) match {
      case null =>
        c.getSuperclass match {
          case null => null
          case parent => inner(parent)
        }
      case annotation => annotation
    }

    Option(inner(klass).asInstanceOf[T])
  }

  private def debugLog(d: Description, deep: Int = 0): Unit = {
    val annotations = d.getAnnotations.asScala.map(_.annotationType.getName).mkString(",")
    val details = s"${d.getMethodName}, ${d.getClassName}, ${d.getTestClass}, $annotations"
    val prefix = "##" + "    " * deep
    System.out.println(s"$prefix ${d.toString} ($details)")
    d.getChildren.forEach(debugLog(_, deep + 1))
  }

  // Copied from JUnit38ClassRunner, added "Category" annotation propagation for ScalaVersionTestSuite
  private def makeDescription(klass: Class[_], test: Test): Description = test match {
    case ts: TestSuite =>
      val name = Option(ts.getName).getOrElse(createSuiteDescriptionName(ts))
      val annotations = ts match {
        case _: ScalaVersionTestSuite => findAnnotation(klass, classOf[Category]).toSeq
        case _                        => Seq()
      }
      val description = Description.createSuiteDescription(name, annotations: _*)
      ts.tests.asScala.foreach { childTest =>
        // compiler fails on TeamCity without this case, no idea why
        //noinspection ScalaRedundantCast
        val childDescription = makeDescription(klass, childTest.asInstanceOf[Test])
        description.addChild(childDescription)
      }
      description
    case tc: TestCase             => Description.createTestDescription(tc.getClass, tc.getName)
    case adapter: Describable     => adapter.getDescription
    case decorator: TestDecorator => makeDescription(klass, decorator.getTest)
    case _                        => Description.createSuiteDescription(test.getClass)
  }

  private def createSuiteDescriptionName(ts: TestSuite): String = {
    val count = ts.countTestCases
    val example = if (count == 0) "" else " [example: %s]".format(ts.testAt(0))
    "TestSuite with %s tests%s".format(count, example)
  }

  // dot is treated as a package separator by IntelliJ which causes broken rendering in tests tree
  private def sanitize(testName: String): String = testName.replace(".", "_")
}

