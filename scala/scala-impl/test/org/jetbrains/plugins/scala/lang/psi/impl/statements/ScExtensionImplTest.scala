package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.jvm.JvmModifier
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.util.ModificationTrackerTester

class ScExtensionImplTest extends ScalaLightCodeInsightFixtureTestCase {

  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  //noinspection UnstableApiUsage,ScalaWrongPlatformMethodsUsage
  def testAccessingModifiersShouldNotIncrementModificationCounters(): Unit = {
    val file = configureFromFileText("a.scala",
      """extension (x: String) def myExtension: String = ???
        |""".stripMargin
    )

    val extensionStatement = file.elements.findByType[ScExtension].get

    val modTrackerTester = new ModificationTrackerTester(getProject)

    //just invoke those methods, ignore results
    extensionStatement.getModifierList
    modTrackerTester.assertPsiModificationCountNotChanged("getModifierList")

    extensionStatement.hasModifierProperty("test2")
    modTrackerTester.assertPsiModificationCountNotChanged("hasModifierProperty")

    extensionStatement.hasModifierPropertyScala("test2")
    modTrackerTester.assertPsiModificationCountNotChanged("hasModifierPropertyScala")

    extensionStatement.hasModifier(JvmModifier.PRIVATE)
    modTrackerTester.assertPsiModificationCountNotChanged("hasModifier")
  }
}