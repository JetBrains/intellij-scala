package org.jetbrains.plugins.scalaDirective.lang.completion

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage

final class ScalaDirectiveDependencyCompletionTest extends ScalaCompletionTestBase {

  /// GROUPS

  def testGroupIdPosition(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testGroupIdPosition_crossVersion(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar_2.12", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testGroupIdPosition_crossVersion2(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar_3", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testGroupIdPosition_fullCrossVersion(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar_2.12.15", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testGroupIdPosition_fullCrossVersion2(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar_3.3.0", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testGroupIdPosition_fullCrossVersion3(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar_2.13.0-RC3", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testGroupIdPosition_inBetween(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo${CARET}something",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testGroupIdPosition_inBetween2(): Unit = {
    PackageSearchApiClient.updateByQueryCache("fo", "", Seq(ApiPackage("foo", "bar", Seq())))
    doCompletionTest(
      fileText = s"//> using dep fo${CARET}something:another-artifact",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  /// ARTIFACTS

  def testArtifactIdPosition(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testArtifactIdPosition_crossVersion(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar_2.12", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testArtifactIdPosition_crossVersion2(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar_3", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo::b$CARET",
      resultText = s"//> using dep foo::bar:$CARET",
      item = "foo::bar:"
    )
  }

  def testArtifactIdPosition_fullCrossVersion(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar_2.12.15", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testArtifactIdPosition_fullCrossVersion2(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar_3.3.0", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testArtifactIdPosition_fullCrossVersion3(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar_2.13.0-RC3", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo:b$CARET",
      resultText = s"//> using dep foo:::bar:$CARET",
      item = "foo:::bar:"
    )
  }

  def testArtifactIdPosition_inBetween(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo:b${CARET}oo",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testArtifactIdPosition_inBetween2(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar", Seq())))
    doCompletionTest(
      fileText = s"//> using dep foo:b${CARET}oo:1.2.3",
      resultText = s"//> using dep foo:bar:$CARET",
      item = "foo:bar:"
    )
  }

  def testNoCompletionWhenSelectedCrossVersionButHaveOnlyRegularArtifact(): Unit = {
    PackageSearchApiClient.updateByQueryCache("foo", "b", Seq(ApiPackage("foo", "bar", Seq())))
    checkNoBasicCompletion(fileText = s"//> using dep foo::b${CARET}oo", item = "foo:bar:")
  }

  /// VERSIONS

  def testVersionCompletion(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", Some(ApiPackage("foo", "bar", Seq("1.2.3"))))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:$CARET",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testVersionCompletion2(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", Some(ApiPackage("foo", "bar", Seq("1.2.3"))))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.$CARET",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testVersionCompletion_inBetween(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", Some(ApiPackage("foo", "bar", Seq("1.2.3"))))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.${CARET}1.0",
      resultText = s"//> using dep foo:bar:1.2.3$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testNoCompletionForVersionWhenPrefixIsDifferent(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", Some(ApiPackage("foo", "bar", Seq("1.2.3"))))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:2$CARET",
      item = "foo:bar:1.2.3"
    )
  }

  def testNoCompletionForVersionWhenNoStableVersionsFound(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", Some(ApiPackage("foo", "bar", Seq("1.2.3-RC1"))))
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:1$CARET",
      item = "foo:bar:1.2.3-RC1"
    )
  }

  def testVersionCompletionWithUnstableOnSecondInvocation(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", Some(ApiPackage("foo", "bar", Seq("1.2.3-RC1"))))
    doCompletionTest(
      fileText = s"//> using dep foo:bar:1.$CARET",
      resultText = s"//> using dep foo:bar:1.2.3-RC1$CARET",
      item = "foo:bar:1.2.3-RC1",
      invocationCount = 2
    )
  }

  def testNoCompletionForVersionWhenNothingFound(): Unit = {
    PackageSearchApiClient.updateByIdCache("foo", "bar", None)
    checkNoBasicCompletion(
      fileText = s"//> using dep foo:bar:2$CARET",
      item = "foo:bar:1.2.3-RC1"
    )
  }
}
