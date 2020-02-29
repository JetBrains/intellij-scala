package org.jetbrains.plugins.scala
package testingSupport.test.utest

import org.jetbrains.plugins.scala.extensions.IteratorExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isInheritorDeep
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

class UTestTestFramework extends AbstractTestFramework {

  override def getName: String = "uTest"

  override def testFileTemplateName = "uTest Object"

  override def getMnemonic: Char = 'm'

  override def getMarkerClassFQName: String = "utest.TestSuite"

  override def getDefaultSuperClass: String = "utest.TestSuite"

  override def suitePaths: Seq[String] = UTestUtil.suitePaths

  override protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String] = Seq()

  override protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq(""""com.lihaoyi" % "utest_2.11" % "latest.integration"""")
    case Some(v) if v.startsWith("2.10") => Seq(""""com.lihaoyi" % "utest_2.10" % "latest.integration"""")
    case _                               => Seq(""""com.lihaoyi" %% "utest" % "latest.integration"""")
  }

  override protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String] = Seq()

  // overridden cause UTest now has 2 marker classes which are equal to suitePathes
  override protected def isTestClass(definition: ScTemplateDefinition): Boolean = {
    if (!definition.isInstanceOf[ScObject]) return false

    val elementScope = ElementScope(definition.getProject)
    val cachedClass = suitePaths.iterator.flatMap(elementScope.getCachedClass).headOption
    cachedClass.exists(isInheritorDeep(definition, _))
  }
}
