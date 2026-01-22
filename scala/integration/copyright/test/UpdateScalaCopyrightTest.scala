package org.jetbrains.plugins.scala.copyright

import com.intellij.copyright.CopyrightManager
import com.maddyhome.idea.copyright.options.LanguageOptions
import com.maddyhome.idea.copyright.pattern.EntityUtil
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory
import com.maddyhome.idea.copyright.{CopyrightProfile, CopyrightUpdaters}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.Calendar

@RunWith(classOf[JUnit4])
final class UpdateScalaCopyrightTest extends ScalaLightCodeInsightFixtureTestCase {
  @Test def blockComment(): Unit = doTest(
    fileText =
      """package foo
        |""".stripMargin,
    resultText =
      """/*
        | * Copyright notice
        | * Over multiple lines
        | */
        |
        |package foo
        |""".stripMargin,
  )

  @Test def blockCommentWithoutPrefix(): Unit = doTest(
    fileText =
      """package foo
        |""".stripMargin,
    resultText =
      """/*
        | Copyright notice
        | Over multiple lines
        | */
        |
        |package foo
        |""".stripMargin,
    customizeLangOptions = _.setPrefixLines(false),
  )

  @Test def lineComment(): Unit = doTest(
    fileText =
      """package foo
        |""".stripMargin,
    resultText =
      """// Copyright notice
        |// Over multiple lines
        |
        |package foo
        |""".stripMargin,
    customizeLangOptions = _.setBlock(false),
  )

  @Test def emptyFile(): Unit = doTest(
    fileText = "",
    resultText =
      """// Copyright notice
        |// Over multiple lines
        |
        |""".stripMargin,
    customizeLangOptions = _.setBlock(false),
  )

  @Test def classDocComment(): Unit = doTest(
    fileText =
      """/** Doc comment */
        |class SomeClass""".stripMargin,
    resultText =
      """/*
        | * Copyright notice
        | * Over multiple lines
        | */
        |
        |/** Doc comment */
        |class SomeClass""".stripMargin
  )

  @Test def multiComments(): Unit = doTest(
    fileText =
      """/* PRESENT 1 */
        |/* PRESENT 2 */
        |// PRESENT 3
        |/* PRESENT */
        |package/* ABSENT 1 */ normal
        |/* ABSENT 2 */
        |""".stripMargin,
    resultText =
      """/* PRESENT 1 */
        |/* PRESENT 2 */
        |// PRESENT 3
        |
        |/*
        | * Copyright notice
        | * Over multiple lines
        | */
        |
        |/* PRESENT */
        |package/* ABSENT 1 */ normal
        |/* ABSENT 2 */
        |""".stripMargin,
    customizeLangOptions = _.setRelativeBefore(false),
  )

  // Here both a regular comment and a doc comment belong to the package as per our PSI structure
  @Test def multiCommentsWithDocComment(): Unit = doTest(
    fileText =
      """/* PRESENT 1 */
        |/* PRESENT 2 */
        |// PRESENT 3
        |/** PRESENT */
        |package/* ABSENT 1 */ normal
        |/* ABSENT 2 */
        |""".stripMargin,
    resultText =
      """/* PRESENT 1 */
        |/* PRESENT 2 */
        |
        |/*
        | * Copyright notice
        | * Over multiple lines
        | */
        |
        |// PRESENT 3
        |/** PRESENT */
        |package/* ABSENT 1 */ normal
        |/* ABSENT 2 */
        |""".stripMargin,
    customizeLangOptions = _.setRelativeBefore(false),
  )

  @Test def noPackage(): Unit = doTest(
    fileText =
      """
        |class Foo {}""".stripMargin,
    resultText =
      """// Copyright notice
        |// Over multiple lines
        |
        |
        |class Foo {}""".stripMargin,
    customizeLangOptions = _.setBlock(false),
  )

  @Test def scalaScript(): Unit = doTest(
    fileText =
      """val foo = 2
        |""".stripMargin,
    resultText =
      """/*
        | * Copyright notice
        | * Over multiple lines
        | */
        |
        |val foo = 2
        |""".stripMargin,
    fileName = "script.sc",
  )

  @Test def scalaScriptWithDirectivesAndCopyrightBefore(): Unit = doTest(
    fileText =
      """//> using scala 2.13.10
        |//> using options -deprecation
        |//> using options -unchecked
        |
        |val foo = 2
        |""".stripMargin,
    resultText =
      """// Copyright notice
        |// Over multiple lines
        |
        |//> using scala 2.13.10
        |//> using options -deprecation
        |//> using options -unchecked
        |
        |val foo = 2
        |""".stripMargin,
    fileName = "script.sc",
    customizeLangOptions = options => {
      options.setBlock(false)
      options.setRelativeBefore(true)
    },
  )

  @Test def scalaScriptWithDirectivesAndCopyrightAfter(): Unit = doTest(
    fileText =
      """//> using scala 2.13.10
        |//> using options -deprecation
        |//> using options -unchecked
        |
        |val foo = 2
        |""".stripMargin,
    resultText =
      """//> using scala 2.13.10
        |//> using options -deprecation
        |
        |// Copyright notice
        |// Over multiple lines
        |
        |//> using options -unchecked
        |
        |val foo = 2
        |""".stripMargin,
    fileName = "script.sc",
    customizeLangOptions = options => {
      options.setBlock(false)
      options.setRelativeBefore(false)
    },
  )

  // fall back to default class name implementation: using file name
  @Test def customCopyrightNoClasses(): Unit = doTest(
    fileText =
      """package foo
        |""".stripMargin,
    resultText =
      s"""/*
         | * Copyright (c) ${Calendar.getInstance.get(Calendar.YEAR)}.
         | * File name: FooBar.scala, class name: FooBar.scala.
         | * Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         | */
         |
         |package foo
         |""".stripMargin,
    fileName = "FooBar.scala",
    copyrightNotice = EntityUtil.encode(
      """Copyright (c) $originalComment.match("Copyright \(c\) (\d+)", 1, "-", "$today.year")$today.year.
        |File name: $file.fileName, class name: $file.className.
        |Lorem ipsum dolor sit amet, consectetur adipiscing elit.""".stripMargin
    )
  )

  // pick class's name
  @Test def customCopyrightOneClass(): Unit = doTest(
    fileText =
      """package foo
        |
        |class SomeClass
        |""".stripMargin,
    resultText =
      s"""/*
         | * Copyright (c) ${Calendar.getInstance.get(Calendar.YEAR)}.
         | * File name: FooBar.scala, class name: SomeClass.
         | * Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         | */
         |
         |package foo
         |
         |class SomeClass
         |""".stripMargin,
    fileName = "FooBar.scala",
    copyrightNotice = EntityUtil.encode(
      """Copyright (c) $originalComment.match("Copyright \(c\) (\d+)", 1, "-", "$today.year")$today.year.
        |File name: $file.fileName, class name: $file.className.
        |Lorem ipsum dolor sit amet, consectetur adipiscing elit.""".stripMargin
    )
  )

  // pick first class's name
  @Test def customCopyrightMultipleClasses(): Unit = doTest(
    fileText =
      """package foo
        |
        |class SomeClass
        |
        |class AnotherClass
        |""".stripMargin,
    resultText =
      s"""/*
         | * Copyright (c) ${Calendar.getInstance.get(Calendar.YEAR)}.
         | * File name: FooBar.scala, class name: SomeClass.
         | * Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         | */
         |
         |package foo
         |
         |class SomeClass
         |
         |class AnotherClass
         |""".stripMargin,
    fileName = "FooBar.scala",
    copyrightNotice = EntityUtil.encode(
      """Copyright (c) $originalComment.match("Copyright \(c\) (\d+)", 1, "-", "$today.year")$today.year.
        |File name: $file.fileName, class name: $file.className.
        |Lorem ipsum dolor sit amet, consectetur adipiscing elit.""".stripMargin
    )
  )

  @Test def customCopyrightUpdateExistingComment(): Unit = doTest(
    fileText =
      """
        |/*
        | * Copyright (c) 2010.
        | * Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        | */
        |
        |package foo
        |""".stripMargin,
    resultText =
      s"""
         |/*
         | * Copyright (c) 2010-${Calendar.getInstance.get(Calendar.YEAR)}.
         | * Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         | */
         |
         |package foo
         |""".stripMargin,
    fileName = "FooBar.scala",
    copyrightNotice = EntityUtil.encode(
      """Copyright (c) $originalComment.match("Copyright \(c\) (\d+)", 1, "-", "$today.year")$today.year.
        |Lorem ipsum dolor sit amet, consectetur adipiscing elit.""".stripMargin
    )
  )

  @Test def customCopyrightUpdateExistingComment2(): Unit = doTest(
    fileText =
      """
        |/*
        | * Copyright (c) 2010-2017.
        | * Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        | */
        |
        |package foo
        |""".stripMargin,
    resultText =
      s"""
         |/*
         | * Copyright (c) 2010-${Calendar.getInstance.get(Calendar.YEAR)}.
         | * Lorem ipsum dolor sit amet, consectetur adipiscing elit.
         | */
         |
         |package foo
         |""".stripMargin,
    fileName = "FooBar.scala",
    copyrightNotice = EntityUtil.encode(
      """Copyright (c) $originalComment.match("Copyright \(c\) (\d+)", 1, "-", "$today.year")$today.year.
        |Lorem ipsum dolor sit amet, consectetur adipiscing elit.""".stripMargin
    )
  )

  private def doTest(
    fileText: String,
    resultText: String,
    fileName: String = getTestName(true) + ".scala",
    copyrightNotice: String = "Copyright notice\nOver multiple lines",
    customizeLangOptions: LanguageOptions => Unit = identity(_),
  ): Unit = {
    configureFromFileText(fileName, fileText)
    configureCopyright(copyrightNotice, customizeLangOptions)
    myFixture.checkResult(resultText, true)
  }

  private def configureCopyright(copyrightNotice: String, customizeLangOptions: LanguageOptions => Unit): Unit = {
    val profile = new CopyrightProfile
    profile.setNotice(copyrightNotice)

    val languageOptions = CopyrightUpdaters.INSTANCE.forFileType(ScalaFileType.INSTANCE).getDefaultOptions
    languageOptions.setFileTypeOverride(LanguageOptions.USE_TEXT)
    customizeLangOptions(languageOptions)
    CopyrightManager.getInstance(getProject).getOptions.setOptions(ScalaFileType.INSTANCE.getName, languageOptions)

    val updateCopyright = UpdateCopyrightFactory.createUpdateCopyright(getProject, getModule, myFixture.getFile, profile)
    if updateCopyright == null then fail("UpdateCopyright could not be created") else {
      updateCopyright.prepare()
      updateCopyright.complete()
    }
  }
}
