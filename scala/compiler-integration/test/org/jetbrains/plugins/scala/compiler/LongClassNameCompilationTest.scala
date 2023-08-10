package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType

abstract class LongClassNameCompilationTestBase_2_10(
  incrementalityType: IncrementalityType,
  useCompileServer: Boolean
) extends ScalaCompilationTestBase(incrementalityType, useCompileServer) {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_10

  def testCompileLongClassNames(): Unit = {
    initBuildProject(
      new SourceFile(
        name = "main",
        classes = Set(
          "OuterLevelWithVeryVeryVeryLongClassName1$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$4d9f7ef5d6ab5c13a152ec9d43c324fd$$$$yVeryLongClassName12$OuterLevelWithVeryVeryVeryLongClassName13$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$4d9f7ef5d6ab5c13a152ec9d43c324fd$$$$yVeryLongClassName12$OuterLevelWithVeryVeryVeryLongClassName13$OuterLevelWithVeryVeryVeryLongClassName14$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$4d9f7ef5d6ab5c13a152ec9d43c324fd$$$$yVeryLongClassName12$OuterLevelWithVeryVeryVeryLongClassName13$OuterLevelWithVeryVeryVeryLongClassName14$OuterLevelWithVeryVeryVeryLongClassName15$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$6facba931fe42f8a8c3cee88c4087$$$$eryVeryLongClassName6$OuterLevelWithVeryVeryVeryLongClassName7$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$6facba931fe42f8a8c3cee88c4087$$$$eryVeryLongClassName6$OuterLevelWithVeryVeryVeryLongClassName7$OuterLevelWithVeryVeryVeryLongClassName8$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$6facba931fe42f8a8c3cee88c4087$$$$eryVeryLongClassName6$OuterLevelWithVeryVeryVeryLongClassName7$OuterLevelWithVeryVeryVeryLongClassName8$OuterLevelWithVeryVeryVeryLongClassName9$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$91b1c5b1d1dbc24da27a88a186643f$$$$yVeryLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$91b1c5b1d1dbc24da27a88a186643f$$$$yVeryLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$OuterLevelWithVeryVeryVeryLongClassName17$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$91b1c5b1d1dbc24da27a88a186643f$$$$yVeryLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$OuterLevelWithVeryVeryVeryLongClassName17$OuterLevelWithVeryVeryVeryLongClassName18$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$d3605f1591402b8a7a269bdf84f8fea1$$$$yVeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$d3605f1591402b8a7a269bdf84f8fea1$$$$yVeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$d3605f1591402b8a7a269bdf84f8fea1$$$$yVeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$d3605f1591402b8a7a269bdf84f8fea1$$$$yVeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$f69aea3521133fca1cacd8294c1ef97$$$$ryVeryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$f69aea3521133fca1cacd8294c1ef97$$$$ryVeryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$OuterLevelWithVeryVeryVeryLongClassName11$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVer$$$$f69aea3521133fca1cacd8294c1ef97$$$$ryVeryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$OuterLevelWithVeryVeryVeryLongClassName11$OuterLevelWithVeryVeryVeryLongClassName12$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$OuterLevelWithVeryVeryVeryLongClassName5$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$OuterLevelWithVeryVeryVeryLongClassName5$OuterLevelWithVeryVeryVeryLongClassName6$",
          "OuterLevelWithVeryVeryVeryLongClassName1"
        ),
        code =
          """object OuterLevelWithVeryVeryVeryLongClassName1 {
            |  object OuterLevelWithVeryVeryVeryLongClassName2 {
            |    object OuterLevelWithVeryVeryVeryLongClassName3 {
            |      object OuterLevelWithVeryVeryVeryLongClassName4 {
            |        object OuterLevelWithVeryVeryVeryLongClassName5 {
            |          object OuterLevelWithVeryVeryVeryLongClassName6 {
            |            object OuterLevelWithVeryVeryVeryLongClassName7 {
            |              object OuterLevelWithVeryVeryVeryLongClassName8 {
            |                object OuterLevelWithVeryVeryVeryLongClassName9 {
            |                  object OuterLevelWithVeryVeryVeryLongClassName10 {
            |                    object OuterLevelWithVeryVeryVeryLongClassName11 {
            |                      object OuterLevelWithVeryVeryVeryLongClassName12 {
            |                        object OuterLevelWithVeryVeryVeryLongClassName13 {
            |                          object OuterLevelWithVeryVeryVeryLongClassName14 {
            |                            object OuterLevelWithVeryVeryVeryLongClassName15 {
            |                              object OuterLevelWithVeryVeryVeryLongClassName16 {
            |                                object OuterLevelWithVeryVeryVeryLongClassName17 {
            |                                  object OuterLevelWithVeryVeryVeryLongClassName18 {
            |                                    object OuterLevelWithVeryVeryVeryLongClassName19 {
            |                                      object OuterLevelWithVeryVeryVeryLongClassName20 {
            |                                        case class MalformedNameExample(x: Int) }}}}}}}}}}}}}}}}}}}}
            |
            |""".stripMargin
      )
    )

    compiler.rebuild().assertNoProblems()
  }
}

class LongClassNameCompilationTest_Idea_Jps_2_10 extends LongClassNameCompilationTestBase_2_10(IncrementalityType.IDEA, false)

class LongClassNameCompilationTest_Idea_Server_2_10 extends LongClassNameCompilationTestBase_2_10(IncrementalityType.IDEA, true)

class LongClassNameCompilationTest_Zinc_Jps_2_10 extends LongClassNameCompilationTestBase_2_10(IncrementalityType.SBT, false)

class LongClassNameCompilationTest_Zinc_Server_2_10 extends LongClassNameCompilationTestBase_2_10(IncrementalityType.SBT, true)

abstract class LongClassNameCompilationTestBase_2_11(
  incrementalityType: IncrementalityType,
  useCompileServer: Boolean
) extends ScalaCompilationTestBase(incrementalityType, useCompileServer) {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11

  def testCompileLongClassNames(): Unit = {
    initBuildProject(
      new SourceFile(
        name = "main",
        classes = Set(
          "OuterLevelWithVeryVeryVeryLongClassName1$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$6c707511eeeac827f7d756bb151d2e7$$$$VeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$6c707511eeeac827f7d756bb151d2e7$$$$VeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$6c707511eeeac827f7d756bb151d2e7$$$$VeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$6c707511eeeac827f7d756bb151d2e7$$$$VeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$6facba931fe42f8a8c3cee88c4087$$$$ryVeryLongClassName6$OuterLevelWithVeryVeryVeryLongClassName7$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$6facba931fe42f8a8c3cee88c4087$$$$ryVeryLongClassName6$OuterLevelWithVeryVeryVeryLongClassName7$OuterLevelWithVeryVeryVeryLongClassName8$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$6facba931fe42f8a8c3cee88c4087$$$$ryVeryLongClassName6$OuterLevelWithVeryVeryVeryLongClassName7$OuterLevelWithVeryVeryVeryLongClassName8$OuterLevelWithVeryVeryVeryLongClassName9$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$8ed8ef869145ece54cd626d22ad05f19$$$$VeryLongClassName12$OuterLevelWithVeryVeryVeryLongClassName13$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$8ed8ef869145ece54cd626d22ad05f19$$$$VeryLongClassName12$OuterLevelWithVeryVeryVeryLongClassName13$OuterLevelWithVeryVeryVeryLongClassName14$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$8ed8ef869145ece54cd626d22ad05f19$$$$VeryLongClassName12$OuterLevelWithVeryVeryVeryLongClassName13$OuterLevelWithVeryVeryVeryLongClassName14$OuterLevelWithVeryVeryVeryLongClassName15$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$964a73eecee29c46aca69e4db6f71df$$$$VeryLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$964a73eecee29c46aca69e4db6f71df$$$$VeryLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$OuterLevelWithVeryVeryVeryLongClassName17$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$964a73eecee29c46aca69e4db6f71df$$$$VeryLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$OuterLevelWithVeryVeryVeryLongClassName17$OuterLevelWithVeryVeryVeryLongClassName18$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$dfc152d63680803075f31bf29b1db998$$$$yVeryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$dfc152d63680803075f31bf29b1db998$$$$yVeryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$OuterLevelWithVeryVeryVeryLongClassName11$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVe$$$$dfc152d63680803075f31bf29b1db998$$$$yVeryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$OuterLevelWithVeryVeryVeryLongClassName11$OuterLevelWithVeryVeryVeryLongClassName12$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$OuterLevelWithVeryVeryVeryLongClassName5$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$OuterLevelWithVeryVeryVeryLongClassName5$OuterLevelWithVeryVeryVeryLongClassName6$",
          "OuterLevelWithVeryVeryVeryLongClassName1"
        ),
        code =
          """object OuterLevelWithVeryVeryVeryLongClassName1 {
            |  object OuterLevelWithVeryVeryVeryLongClassName2 {
            |    object OuterLevelWithVeryVeryVeryLongClassName3 {
            |      object OuterLevelWithVeryVeryVeryLongClassName4 {
            |        object OuterLevelWithVeryVeryVeryLongClassName5 {
            |          object OuterLevelWithVeryVeryVeryLongClassName6 {
            |            object OuterLevelWithVeryVeryVeryLongClassName7 {
            |              object OuterLevelWithVeryVeryVeryLongClassName8 {
            |                object OuterLevelWithVeryVeryVeryLongClassName9 {
            |                  object OuterLevelWithVeryVeryVeryLongClassName10 {
            |                    object OuterLevelWithVeryVeryVeryLongClassName11 {
            |                      object OuterLevelWithVeryVeryVeryLongClassName12 {
            |                        object OuterLevelWithVeryVeryVeryLongClassName13 {
            |                          object OuterLevelWithVeryVeryVeryLongClassName14 {
            |                            object OuterLevelWithVeryVeryVeryLongClassName15 {
            |                              object OuterLevelWithVeryVeryVeryLongClassName16 {
            |                                object OuterLevelWithVeryVeryVeryLongClassName17 {
            |                                  object OuterLevelWithVeryVeryVeryLongClassName18 {
            |                                    object OuterLevelWithVeryVeryVeryLongClassName19 {
            |                                      object OuterLevelWithVeryVeryVeryLongClassName20 {
            |                                        case class MalformedNameExample(x: Int) }}}}}}}}}}}}}}}}}}}}
            |
            |""".stripMargin
      )
    )

    compiler.rebuild().assertNoProblems()
  }
}

class LongClassNameCompilationTest_Idea_Jps_2_11 extends LongClassNameCompilationTestBase_2_11(IncrementalityType.IDEA, false)

class LongClassNameCompilationTest_Idea_Server_2_11 extends LongClassNameCompilationTestBase_2_11(IncrementalityType.IDEA, true)

class LongClassNameCompilationTest_Zinc_Jps_2_11 extends LongClassNameCompilationTestBase_2_11(IncrementalityType.SBT, false)

class LongClassNameCompilationTest_Zinc_Server_2_11 extends LongClassNameCompilationTestBase_2_11(IncrementalityType.SBT, true)

abstract class LongClassNameCompilationTestBase_2_12(
  incrementalityType: IncrementalityType,
  useCompileServer: Boolean
) extends LongClassNameCompilationTestBase_2_11(incrementalityType, useCompileServer) {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class LongClassNameCompilationTest_Idea_Jps_2_12 extends LongClassNameCompilationTestBase_2_12(IncrementalityType.IDEA, false)

class LongClassNameCompilationTest_Idea_Server_2_12 extends LongClassNameCompilationTestBase_2_12(IncrementalityType.IDEA, true)

class LongClassNameCompilationTest_Zinc_Jps_2_12 extends LongClassNameCompilationTestBase_2_12(IncrementalityType.SBT, false)

class LongClassNameCompilationTest_Zinc_Server_2_12 extends LongClassNameCompilationTestBase_2_12(IncrementalityType.SBT, true)

abstract class LongClassNameCompilationTestBase_2_13(
  incrementalityType: IncrementalityType,
  useCompileServer: Boolean
) extends ScalaCompilationTestBase(incrementalityType, useCompileServer) {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testCompileLongClassNames(): Unit = {
    initBuildProject(
      new SourceFile(
        name = "main",
        classes = Set(
          "OuterLevelWithVeryVeryVeryLongClassName1$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$21cd1be25088dedb16ca1b3c346466fb$$$$eryLongClassName7$OuterLevelWithVeryVeryVeryLongClassName8$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$21cd1be25088dedb16ca1b3c346466fb$$$$eryLongClassName7$OuterLevelWithVeryVeryVeryLongClassName8$OuterLevelWithVeryVeryVeryLongClassName9$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$361c0db7030df22c21772c193d391$$$$yLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$361c0db7030df22c21772c193d391$$$$yLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$361c0db7030df22c21772c193d391$$$$yLongClassName19$OuterLevelWithVeryVeryVeryLongClassName20$MalformedNameExample",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$7e70169f8f612a14697f8fd52119750$$$$yLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$7e70169f8f612a14697f8fd52119750$$$$yLongClassName15$OuterLevelWithVeryVeryVeryLongClassName16$OuterLevelWithVeryVeryVeryLongClassName17$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$973a10bc1cd9792c988bf49bee47b56$$$$ryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$973a10bc1cd9792c988bf49bee47b56$$$$ryLongClassName9$OuterLevelWithVeryVeryVeryLongClassName10$OuterLevelWithVeryVeryVeryLongClassName11$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$9a7e92c93110f2442b3ebd9c453788$$$$yLongClassName11$OuterLevelWithVeryVeryVeryLongClassName12$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$9a7e92c93110f2442b3ebd9c453788$$$$yLongClassName11$OuterLevelWithVeryVeryVeryLongClassName12$OuterLevelWithVeryVeryVeryLongClassName13$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$9f428b41847a5866a8323f5944a36d$$$$yLongClassName13$OuterLevelWithVeryVeryVeryLongClassName14$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$9f428b41847a5866a8323f5944a36d$$$$yLongClassName13$OuterLevelWithVeryVeryVeryLongClassName14$OuterLevelWithVeryVeryVeryLongClassName15$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$d292b257125352e68f2f62fd843b554$$$$yLongClassName17$OuterLevelWithVeryVeryVeryLongClassName18$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$d292b257125352e68f2f62fd843b554$$$$yLongClassName17$OuterLevelWithVeryVeryVeryLongClassName18$OuterLevelWithVeryVeryVeryLongClassName19$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$f1136b7659b86ea0804013acf91cbef$$$$eryLongClassName5$OuterLevelWithVeryVeryVeryLongClassName6$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVer$$$$f1136b7659b86ea0804013acf91cbef$$$$eryLongClassName5$OuterLevelWithVeryVeryVeryLongClassName6$OuterLevelWithVeryVeryVeryLongClassName7$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$",
          "OuterLevelWithVeryVeryVeryLongClassName1$OuterLevelWithVeryVeryVeryLongClassName2$OuterLevelWithVeryVeryVeryLongClassName3$OuterLevelWithVeryVeryVeryLongClassName4$OuterLevelWithVeryVeryVeryLongClassName5$",
          "OuterLevelWithVeryVeryVeryLongClassName1"
        ),
        code =
          """object OuterLevelWithVeryVeryVeryLongClassName1 {
            |  object OuterLevelWithVeryVeryVeryLongClassName2 {
            |    object OuterLevelWithVeryVeryVeryLongClassName3 {
            |      object OuterLevelWithVeryVeryVeryLongClassName4 {
            |        object OuterLevelWithVeryVeryVeryLongClassName5 {
            |          object OuterLevelWithVeryVeryVeryLongClassName6 {
            |            object OuterLevelWithVeryVeryVeryLongClassName7 {
            |              object OuterLevelWithVeryVeryVeryLongClassName8 {
            |                object OuterLevelWithVeryVeryVeryLongClassName9 {
            |                  object OuterLevelWithVeryVeryVeryLongClassName10 {
            |                    object OuterLevelWithVeryVeryVeryLongClassName11 {
            |                      object OuterLevelWithVeryVeryVeryLongClassName12 {
            |                        object OuterLevelWithVeryVeryVeryLongClassName13 {
            |                          object OuterLevelWithVeryVeryVeryLongClassName14 {
            |                            object OuterLevelWithVeryVeryVeryLongClassName15 {
            |                              object OuterLevelWithVeryVeryVeryLongClassName16 {
            |                                object OuterLevelWithVeryVeryVeryLongClassName17 {
            |                                  object OuterLevelWithVeryVeryVeryLongClassName18 {
            |                                    object OuterLevelWithVeryVeryVeryLongClassName19 {
            |                                      object OuterLevelWithVeryVeryVeryLongClassName20 {
            |                                        case class MalformedNameExample(x: Int) }}}}}}}}}}}}}}}}}}}}
            |
            |""".stripMargin
      )
    )

    compiler.rebuild().assertNoProblems()
  }
}

class LongClassNameCompilationTest_Idea_Jps_2_13 extends LongClassNameCompilationTestBase_2_13(IncrementalityType.IDEA, false)

class LongClassNameCompilationTest_Idea_Server_2_13 extends LongClassNameCompilationTestBase_2_13(IncrementalityType.IDEA, true)

class LongClassNameCompilationTest_Zinc_Jps_2_13 extends LongClassNameCompilationTestBase_2_13(IncrementalityType.SBT, false)

class LongClassNameCompilationTest_Zinc_Server_2_13 extends LongClassNameCompilationTestBase_2_13(IncrementalityType.SBT, true)

abstract class LongClassNameCompilationTestBase_3(
  incrementalityType: IncrementalityType,
  useCompileServer: Boolean
) extends LongClassNameCompilationTestBase_2_13(incrementalityType, useCompileServer) {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def classFileNames(className: String)(implicit version: ScalaVersion): Set[String] = {
    val suffixes =
      if (className == "OuterLevelWithVeryVeryVeryLongClassName1") Set("class", "tasty")
      else Set("class")
    suffixes.map(suffix => s"$className.$suffix")
  }
}

class LongClassNameCompilationTest_Idea_Jps_3 extends LongClassNameCompilationTestBase_3(IncrementalityType.IDEA, false)

class LongClassNameCompilationTest_Idea_Server_3 extends LongClassNameCompilationTestBase_3(IncrementalityType.IDEA, true)

class LongClassNameCompilationTest_Zinc_Jps_3 extends LongClassNameCompilationTestBase_3(IncrementalityType.SBT, false)

class LongClassNameCompilationTest_Zinc_Server_3 extends LongClassNameCompilationTestBase_3(IncrementalityType.SBT, true)
