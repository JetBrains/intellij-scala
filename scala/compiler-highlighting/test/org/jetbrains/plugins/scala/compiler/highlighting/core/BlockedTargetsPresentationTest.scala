package org.jetbrains.plugins.scala.compiler.highlighting.core

import org.jetbrains.plugins.scala.compiler.highlighting.core.BlockedTargetsPresentation.{capped, shownNames}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.CompilationStateTestBase.CompilerHighlightingCoreTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Tag, Test}

/** How [[BlockedTargetsPresentation]] names blockers, given each one's full name and sbt project name. */
@Tag(CompilerHighlightingCoreTest)
class BlockedTargetsPresentationTest:

  @Test
  def blockersAreShownByTheirSbtProjectName(): Unit =
    assertEquals(Seq("C", "D"), shownNames(Map("root.C" -> Some("C"), "root.D" -> Some("D"))))

  @Test
  def blockersSharingAnSbtProjectNameAreShownByTheirFullName(): Unit =
    assertEquals(
      Seq("C", "other.core", "root.core"),
      shownNames(Map("root.core" -> Some("core"), "other.core" -> Some("core"), "root.C" -> Some("C")))
    )

  @Test
  def aBlockerWithoutAnSbtProjectNameIsShownByItsFullName(): Unit =
    assertEquals(Seq("C", "lib"), shownNames(Map("root.C" -> Some("C"), "lib" -> None)))

  @Test
  def anSbtProjectNameEqualToAnotherBlockersFullNameIsNotShown(): Unit =
    assertEquals(Seq("core", "root.core"), shownNames(Map("root.core" -> Some("core"), "core" -> None)))

  @Test
  def namesAreSorted(): Unit =
    assertEquals(Seq("A", "B", "C"), shownNames(Map("root.C" -> Some("C"), "root.A" -> Some("A"), "root.B" -> Some("B"))))

  @Test
  def upToThreeNamesAreAllListed(): Unit =
    assertEquals((Seq("A", "B", "C"), 0), capped(Seq("A", "B", "C")))

  @Test
  def moreThanThreeNamesListTheFirstTwo(): Unit =
    assertEquals((Seq("A", "B"), 2), capped(Seq("A", "B", "C", "D")))

  @Test
  def manyNamesListTheFirstTwo(): Unit =
    assertEquals((Seq("A", "B"), 8), capped(Seq.tabulate(10)(i => ('A' + i).toChar.toString)))
