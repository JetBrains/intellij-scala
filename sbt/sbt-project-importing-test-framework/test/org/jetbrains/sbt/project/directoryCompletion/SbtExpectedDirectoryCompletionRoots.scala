package org.jetbrains.sbt.project.directoryCompletion

import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}

object SbtExpectedDirectoryCompletionRoots {

  val DefaultSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultSbtContentRootsScala2("2.12")

  val DefaultSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultSbtContentRootsScala2("2.13")

  private def defaultSbtContentRootsScala2(scalaBinVer: String): Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("src/main/java", JavaSourceRootType.SOURCE),
    ("src/main/scala", JavaSourceRootType.SOURCE),
    ("src/main/scala-2", JavaSourceRootType.SOURCE),
    (s"src/main/scala-$scalaBinVer", JavaSourceRootType.SOURCE),
    ("src/test/java", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala-2", JavaSourceRootType.TEST_SOURCE),
    (s"src/test/scala-$scalaBinVer", JavaSourceRootType.TEST_SOURCE),
    ("src/main/resources", JavaResourceRootType.RESOURCE),
    ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  val DefaultMainSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultMainSbtContentRootsScala2(13)

  val DefaultTestSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultTestSbtContentRootsScala2(13)

  val DefaultMainSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultMainSbtContentRootsScala2(12)

  val DefaultTestSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultTestSbtContentRootsScala2(12)

  private def defaultMainSbtContentRootsScala2(minorVersion: Integer): Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.SOURCE),
    ("scala", JavaSourceRootType.SOURCE),
    ("scala-2", JavaSourceRootType.SOURCE),
    (s"scala-2.$minorVersion", JavaSourceRootType.SOURCE),
    ("resources", JavaResourceRootType.RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  private def defaultTestSbtContentRootsScala2(minorVersion: Integer): Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-2", JavaSourceRootType.TEST_SOURCE),
    (s"scala-2.$minorVersion", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  val DefaultSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("src/main/java", JavaSourceRootType.SOURCE),
    ("src/main/scala", JavaSourceRootType.SOURCE),
    ("src/main/scala-3", JavaSourceRootType.SOURCE),
    ("src/test/java", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala-3", JavaSourceRootType.TEST_SOURCE),
    ("src/main/resources", JavaResourceRootType.RESOURCE),
    ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  val DefaultMainSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.SOURCE),
    ("scala", JavaSourceRootType.SOURCE),
    ("scala-3", JavaSourceRootType.SOURCE),
    ("resources", JavaResourceRootType.RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  val DefaultTestSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-3", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map(ExpectedDirectoryCompletionVariant.apply.tupled)
}
