package org.jetbrains.sbt.editor.documentationProvider

trait SbtDocumentationProviderCommonTests {
  self: SbtDocumentationProviderTestBase =>

  def testSbtDescriptionShouldBeWrappedInDefaultScaladocTemplate(): Unit = doGenerateDocTest(
    s"""val ${CARET}someKey = SettingKey[Int]("some-key", "This is description for some-key")""",
    s"""<html><body>
       |<pre>Pattern: <b>someKey</b>: <a href="psi_element://sbt.SettingKey"><code>SettingKey</code></a>[Int]</pre>
       |<br/><b>This is description for some-key</b>
       |</body></html>
       |""".stripMargin
  )

  def testSettingKey(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = SettingKey[Int]("some-key", "$description")""",
    description
  )

  def testAttributeKey(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = AttributeKey[Int]("some-key", "$description")""",
    description
  )

  def testTaskKey(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = TaskKey[Int]("some-key", "$description")""",
    description
  )

  def testInputKey(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = InputKey[Int]("some-key", "$description")""",
    description
  )

  def testKeyWithLabelAndDescription(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = SettingKey[Int]("some-key", "$description")""",
    description
  )

  def testKeyWithLabelAndDescriptionAndRank(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = SettingKey[Int]("some-key", "$description", KeyRanks.APlusSetting)""",
    description
  )

  def testKeyWithLabelAndDescriptionAndExtend(): Unit = doShortGenerateDocTest(
    s"""val someKey = SettingKey[Int]("some-key")
       |val ${CARET}someKey1 = SettingKey[Int]("some-key-1", "$description", someKey)
       |""".stripMargin,
    description
  )

  def testKeyWithLabelAndDescriptionAndRankAndExtend(): Unit = doShortGenerateDocTest(
    s"""val someKey = SettingKey[Int]("some-other-key")
       |val ${CARET}someKey1 = SettingKey[Int]("some-key", "$description", KeyRanks.APlusSetting, someOtherKey)
       |""".stripMargin,
    description
  )

  def testKeyWithReferenceDescription(): Unit = doShortGenerateDocTest(
    s"""val someValue: String = "some text"
       |val ${CARET}someKey = SettingKey[Int](someValue)
       |""".stripMargin,
    s"""<i>someValue</i>"""
  )

  def testUseLabelAsDescriptionIfDescriptionIsMissing(): Unit = doShortGenerateDocTest(
    s"""val ${CARET}someKey = SettingKey[Int]("some-key-label")""",
    "some-key-label"
  )

  def testDoNotDetectDocumentationForNonKeyApplyMethod(): Unit = doGenerateDocTest(
    s"""val ${CARET}someKey = SomeUnknownClass[Int]("some-key", "$description")""",
    null
  )
}
