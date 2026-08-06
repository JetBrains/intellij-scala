package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

class ScDocTagImplTest extends ScalaLightCodeInsightFixtureTestCase {
  def testTagGetName(): Unit = {
    configureFromFileText(
      """/**
        | * @note some note
        | */
        |""".stripMargin
    )

    val tag = getFile.elements.findByType[ScDocTag].get
    assertEquals("Tag name", "note", tag.getName)
  }
}