package org.jetbrains.plugins.scala.util.runners

import junit.framework.TestCase
import org.jetbrains.plugins.scala.base.InjectableJdk
import org.jetbrains.plugins.scala.util.Annotations
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils.Status.Warning

object MultipleScalaVersionsRunner {

  private val DefaultScalaVersionsToRun: Seq[TestScalaVersion] =
    Seq(
      TestScalaVersion.Scala_2_11,
      TestScalaVersion.Scala_2_12,
      TestScalaVersion.Scala_2_13,
    )

  private val DefaultJdkVersionToRun: TestJdkVersion =
    TestJdkVersion.from(InjectableJdk.DefaultJdk)

  lazy val filterJdkVersionRegistry: Option[TestJdkVersion] = {
    val result = Option(System.getProperty("filter.test.jdk.version")).map(TestJdkVersion.valueOf)
    result.foreach(v => TeamcityUtils.logUnderTeamcity(s"MultipleScalaVersionsRunner: running jdk filter: $v", status = Warning))
    result
  }

  private[runners] def scalaVersionsToRun(klass: Class[_ <: TestCase]): Seq[TestScalaVersion] = {
    val annotation = Annotations.findAnnotation(klass, classOf[RunWithScalaVersions])
    annotation
      .map(_.value.toSeq)
      .getOrElse(DefaultScalaVersionsToRun)
  }

  private[runners] def jdkVersionsToRun(klass: Class[_ <: TestCase]): Seq[TestJdkVersion] = {
    val annotation = Annotations.findAnnotation(klass, classOf[RunWithJdkVersions])
    annotation
      .map(_.value.toSeq)
      .getOrElse(Seq(DefaultJdkVersionToRun))
  }
}
