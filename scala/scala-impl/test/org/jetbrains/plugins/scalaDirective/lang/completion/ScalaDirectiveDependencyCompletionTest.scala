package org.jetbrains.plugins.scalaDirective.lang.completion

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.packagesearch.api.{PackageSearchClient, PackageSearchClientTesting}

import java.util.Arrays.asList

final class ScalaDirectiveDependencyCompletionTest
  extends ScalaCompletionTestBase
    with PackageSearchClientTesting {

  /// GROUPS

  def testGroupIdPosition(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testGroupIdPosition_crossVersion(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar_2.12", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testGroupIdPosition_crossVersion2(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar_3", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testGroupIdPosition_fullCrossVersion(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar_2.12.15", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testGroupIdPosition_fullCrossVersion2(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar_3.3.0", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testGroupIdPosition_fullCrossVersion3(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar_2.13.0-RC3", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testGroupIdPosition_inBetween(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo${CARET}something",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testGroupIdPosition_inBetween2(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("fo", "", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep fo${CARET}something:another-artifact",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  /// ARTIFACTS

  def testArtifactIdPosition(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testArtifactIdPosition_crossVersion(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar_2.12", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testArtifactIdPosition_crossVersion2(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar_3", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo::b$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testArtifactIdPosition_fullCrossVersion(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar_2.12.15", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testArtifactIdPosition_fullCrossVersion2(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar_3.3.0", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testArtifactIdPosition_fullCrossVersion3(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar_2.13.0-RC3", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testArtifactIdPosition_inBetween(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo:b${CARET}oo",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testArtifactIdPosition_inBetween2(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    doCompletionTest(
      fileText = s"//> using dep foo:b${CARET}oo:1.2.3",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testNoCompletionWhenSelectedCrossVersionButHaveOnlyRegularArtifact(): Unit = {
    PackageSearchClient.instance().updateByQueryCache("foo", "b", asList(apiMavenPackage("foo", "bar", emptyVersionsContainer())))
    checkNoBasicCompletion(fileText = s"//> using dep foo::b${CARET}oo", item = "foo:bar:")
  }

  /// VERSIONS

  def testVersionCompletion(): Unit = {
    PackageSearchClient.instance().updateByIdCache("foo", "bar", apiMavenPackage("foo", "bar", versionsContainer("1.2.3")))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:$CARET",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testVersionCompletion2(): Unit = {
    PackageSearchClient.instance().updateByIdCache("foo", "bar", apiMavenPackage("foo", "bar", versionsContainer("1.2.3")))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.$CARET",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testVersionCompletion_inBetween(): Unit = {
    PackageSearchClient.instance().updateByIdCache("foo", "bar", apiMavenPackage("foo", "bar", versionsContainer("1.2.3")))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.${CARET}1.0",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testNoCompletionForVersionWhenPrefixIsDifferent(): Unit = {
    PackageSearchClient.instance().updateByIdCache("foo", "bar", apiMavenPackage("foo", "bar", versionsContainer("1.2.3")))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:2$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testNoCompletionForVersionWhenNoStableVersionsFound(): Unit = {
    PackageSearchClient.instance().updateByIdCache("foo", "bar", apiMavenPackage("foo", "bar", versionsContainer("1.2.3-RC1", None)))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:1$CARET",
      item = "foo:bar:1.2.3-RC1"
    )
  }

  def testVersionCompletionWithUnstableOnSecondInvocation(): Unit = {
    PackageSearchClient.instance().updateByIdCache("foo", "bar", apiMavenPackage("foo", "bar", versionsContainer("1.2.3-RC1", None)))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.$CARET",
      resultText = s"//> using dep foo:bar:1.2.3-RC1$CARET",
      item = "foo:bar:1.2.3-RC1",
      invocationCount = 2
    )
  }

  def testNoCompletionForVersionWhenNothingFound(): Unit = {
    PackageSearchClient.instance().updateByIdCache("foo", "bar", null)
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:2$CARET",
      item = "foo:bar:1.2.3-RC1"
    )
  }
}
