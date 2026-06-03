package org.jetbrains.plugins.scala.testingSupport.scalatest.base

/** See [[org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestMigrationUtils]] */
trait ScalaTestApiSymbols {
  protected val ImportsForFeatureSpec: String
  protected val ImportsForFlatSpec: String
  protected val ImportsForFreeSpec: String
  protected val ImportsForFunSpec: String
  protected val ImportsForFunSuite: String
  protected val ImportsForPathFreeSpec: String
  protected val ImportsForPropSpec: String
  protected val ImportsForWordSpec: String

  protected val FeatureSpecBase: String
  protected val FlatSpecBase: String
  protected val FreeSpecBase: String
  protected val FunSpecBase: String
  protected val FunSuiteBase: String
  protected val PathFreeSpecBase: String
  protected val PropSpecBase: String
  protected val WordSpecBase: String

  protected val featureSpecApi: FeatureSpecApi
}

object ScalaTestApiSymbols {

  /** @inheritdoc */
  trait BeforeScalatest_3_2 extends ScalaTestApiSymbols {
    private val baseScalatestImport: String = s"import org.scalatest._"

    override protected val ImportsForFeatureSpec: String = baseScalatestImport
    override protected val ImportsForFlatSpec: String = baseScalatestImport
    override protected val ImportsForFreeSpec: String = baseScalatestImport
    override protected val ImportsForFunSpec: String = baseScalatestImport
    override protected val ImportsForFunSuite: String = baseScalatestImport
    override protected val ImportsForPathFreeSpec: String = baseScalatestImport
    override protected val ImportsForPropSpec: String = baseScalatestImport
    override protected val ImportsForWordSpec: String = baseScalatestImport

    override protected val FeatureSpecBase = "FeatureSpec"
    override protected val FlatSpecBase = "FlatSpec"
    override protected val FreeSpecBase = "FreeSpec"
    override protected val FunSpecBase = "FunSpec"
    override protected val FunSuiteBase = "FunSuite"
    override protected val PathFreeSpecBase = "path.FreeSpec"
    override protected val PropSpecBase = "PropSpec"
    override protected val WordSpecBase = "WordSpec"

    override protected val featureSpecApi: FeatureSpecApi = FeatureSpecApi.BeforeScalatest32
  }

  /** @inheritdoc */
  trait SinceScalatest_3_2 extends ScalaTestApiSymbols {
    private def withBaseScalatestImport(importText: String): String =
      s"import org.scalatest._ ; $importText"

    override protected val ImportsForFeatureSpec: String = withBaseScalatestImport("import org.scalatest.featurespec._")
    override protected val ImportsForFlatSpec: String = withBaseScalatestImport("import org.scalatest.flatspec._")
    override protected val ImportsForFreeSpec: String = withBaseScalatestImport("import org.scalatest.freespec._")
    override protected val ImportsForFunSpec: String = withBaseScalatestImport("import org.scalatest.funspec._")
    override protected val ImportsForFunSuite: String = withBaseScalatestImport("import org.scalatest.funsuite._")
    override protected val ImportsForPathFreeSpec: String = withBaseScalatestImport("import org.scalatest.freespec._")
    override protected val ImportsForPropSpec: String = withBaseScalatestImport("import org.scalatest.propspec._")
    override protected val ImportsForWordSpec: String = withBaseScalatestImport("import org.scalatest.wordspec._")

    override protected val FeatureSpecBase = "AnyFeatureSpec"
    override protected val FlatSpecBase = "AnyFlatSpec"
    override protected val FreeSpecBase = "AnyFreeSpec"
    override protected val FunSpecBase = "AnyFunSpec"
    override protected val FunSuiteBase = "AnyFunSuite"
    override protected val PathFreeSpecBase = "PathAnyFreeSpec"
    override protected val PropSpecBase = "AnyPropSpec"
    override protected val WordSpecBase = "AnyWordSpec"

    override protected val featureSpecApi: FeatureSpecApi = FeatureSpecApi.SinceScalatest32
  }
}

trait FeatureSpecApi {
  def featureMethodName: String
  def scenarioMethodName: String
}

object FeatureSpecApi {
  object BeforeScalatest32 extends FeatureSpecApi {
    override def featureMethodName: String = "feature"
    override def scenarioMethodName: String = "scenario"
  }

  object SinceScalatest32 extends FeatureSpecApi {
    override def featureMethodName: String = "Feature"
    override def scenarioMethodName: String = "Scenario"
  }
}