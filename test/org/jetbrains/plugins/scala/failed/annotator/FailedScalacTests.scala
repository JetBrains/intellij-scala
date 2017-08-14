package org.jetbrains.plugins.scala.failed.annotator

import java.io.File
import java.nio.file.Files

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.libraryLoaders.{JdkLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.projectHighlighting.SeveralFilesHighlightingTest
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.reporter.ConsoleReporter
import org.junit.experimental.categories.Category

import scala.reflect.NameTransformer


/**
  * Nikolay.Tropin
  * 14-Aug-17
  */
@Category(Array(classOf[PerfCycleTests]))
class FailedScalacTests extends ScalaLightCodeInsightFixtureTestAdapter with SeveralFilesHighlightingTest {

  override def getProject = super.getProject

  override def filesToHighlight: Array[File] = {
    val testDataDir = TestUtils.getTestDataPath + "/scalacTests/failed/"

    val fileName = getTestName(/*lowercaseFirstLetter*/ false).stripPrefix("_") + ".scala"
    val decoded = NameTransformer.decode(fileName)
    val file = new File(testDataDir + decoded)
    assert(file.exists())
    Array(file)
  }

  override def librariesLoaders = Seq(
    ScalaLibraryLoader(isIncludeReflectLibrary = true),
    JdkLoader()
  )

  //todo: these tests freeze IDEA
//  def `test_existential-slow-compile1`: Unit = doTest()
//  def `test_existential-slow-compile2`: Unit = doTest()

  //Delete test method and move corresponding .scala file or directory to testdata/scalacTests/pos/ after test passes

  def test_arrays2(): Unit = doTest()
  def test_arrays3(): Unit = doTest()
  def test_channels(): Unit = doTest()
  def test_compound(): Unit = doTest()
  def test_CustomGlobal(): Unit = doTest()
  def `test_cycle-jsoup`(): Unit = doTest()
  def test_depmet_implicit_oopsla_session(): Unit = doTest()
  def test_depmet_implicit_oopsla_session_2(): Unit = doTest()
  def test_depmet_implicit_oopsla_session_simpler(): Unit = doTest()
  def test_existentials(): Unit = doTest()
  def `test_extractor-types`(): Unit = doTest()
  def test_fun_undo_eta(): Unit = doTest()
  def `test_gadt-gilles`(): Unit = doTest()
  def test_gadts2(): Unit = doTest()
  def test_hkgadt(): Unit = doTest()
  def `test_implicits-new`(): Unit = doTest()
  def `test_implicits-old`(): Unit = doTest()
  def test_infersingle(): Unit = doTest()
  def test_infer_override_def_args(): Unit = doTest()
  def `test_javaConversions-2.10-ambiguity`(): Unit = doTest()
  def `test_javaConversions-2.10-regression`(): Unit = doTest()
  def `test_macro-bundle-disambiguate-bundle`(): Unit = doTest()
  def `test_macro-bundle-disambiguate-nonbundle`(): Unit = doTest()
  def `test_macro-implicit-invalidate-on-error`(): Unit = doTest()
  def test_matthias4(): Unit = doTest()
  def `test_native-warning`(): Unit = doTest()
  def test_optmatch(): Unit = doTest()
  def `test_overloaded-unapply`(): Unit = doTest()
  def test_overloaded_ho_fun(): Unit = doTest()
  def test_patmat(): Unit = doTest()
  def test_presuperContext(): Unit = doTest()
  def `test_reflection-compat-api-universe`(): Unit = doTest()
  def `test_reflection-compat-macro-universe`(): Unit = doTest()
  def `test_reflection-compat-ru`(): Unit = doTest()
  def test_sammy_ctor_arg(): Unit = doTest()
  def test_sammy_exist(): Unit = doTest()
  def test_sammy_extends_function(): Unit = doTest()
  def test_sammy_implicit(): Unit = doTest()
  def test_sammy_infer_argtype_subtypes(): Unit = doTest()
  def test_sammy_overload(): Unit = doTest()
  def test_sammy_override(): Unit = doTest()
  def test_sammy_poly(): Unit = doTest()
  def test_sammy_scope(): Unit = doTest()
  def test_sammy_single(): Unit = doTest()
  def test_sammy_twice(): Unit = doTest()
  def `test_scala-singleton`(): Unit = doTest()
  def `test_strip-tvars-for-lubbasetypes`(): Unit = doTest()
  def test_t0438(): Unit = doTest()
  def test_t1085(): Unit = doTest()
  def test_t1279a(): Unit = doTest()
  def test_t1803(): Unit = doTest()
  def test_t1957(): Unit = doTest()
  def test_t2293(): Unit = doTest()
  def test_t267(): Unit = doTest()
  def `test_t2712-5`(): Unit = doTest()
  def test_t2913(): Unit = doTest()
  def test_t3177(): Unit = doTest()
  def test_t3240(): Unit = doTest()
  def test_t3688(): Unit = doTest()
  def test_t3866(): Unit = doTest()
  def test_t3880(): Unit = doTest()
  def test_t389(): Unit = doTest()
  def test_t3999b(): Unit = doTest()
  def test_t4273(): Unit = doTest()
  def test_t482(): Unit = doTest()
  def test_t4853(): Unit = doTest()
  def test_t4957(): Unit = doTest()
  def test_t5294c(): Unit = doTest()
  def test_t5313(): Unit = doTest()
  def test_t5317(): Unit = doTest()
  def test_t5444(): Unit = doTest()
  def test_t5626(): Unit = doTest()
  def test_t5683(): Unit = doTest()
  def test_t5706(): Unit = doTest()
  def test_t5729(): Unit = doTest()
  def test_t5845(): Unit = doTest()
  def test_t5953(): Unit = doTest()
  def test_t5958(): Unit = doTest()
  def test_t6047(): Unit = doTest()
  def test_t6084(): Unit = doTest()
  def test_t6205(): Unit = doTest()
  def test_t6221(): Unit = doTest()
  def test_t6447(): Unit = doTest()
  def test_t6516(): Unit = doTest()
  def test_t6675(): Unit = doTest()
  def test_t6778(): Unit = doTest()
  def test_t6846(): Unit = doTest()
  def test_t694(): Unit = doTest()
  def test_t697(): Unit = doTest()
  def test_t7228(): Unit = doTest()
  def test_t7517(): Unit = doTest()
  def test_t7520(): Unit = doTest()
  def test_t762(): Unit = doTest()
  def test_t7649(): Unit = doTest()
  def test_t7668(): Unit = doTest()
  def test_t7688(): Unit = doTest()
  def test_t7704(): Unit = doTest()
  def test_t7776(): Unit = doTest()
  def test_t7944(): Unit = doTest()
  def `test_t8002-nested-scope`(): Unit = doTest()
  def test_t802(): Unit = doTest()
  def test_t8044(): Unit = doTest()
  def test_t8079b(): Unit = doTest()
  def test_t8128(): Unit = doTest()
  def test_t8177h(): Unit = doTest()
  def test_t8187(): Unit = doTest()
  def test_t8237(): Unit = doTest()
  def test_t8267(): Unit = doTest()
  def test_t8310(): Unit = doTest()
  def test_t8429(): Unit = doTest()
  def test_t845(): Unit = doTest()
  def test_t8498(): Unit = doTest()
  def test_t8523(): Unit = doTest()
  def test_t9008(): Unit = doTest()
  def test_t9116(): Unit = doTest()
  def test_t9449(): Unit = doTest()
  def test_t9498(): Unit = doTest()
  def test_t9658(): Unit = doTest()
  def test_tcpoly_infer_implicit_tuple_wrapper(): Unit = doTest()
  def test_tcpoly_seq(): Unit = doTest()
  def test_ticket2251(): Unit = doTest()
  def `test_typerep-stephane`(): Unit = doTest()
  def test_virtpatmat_gadt_array(): Unit = doTest()
  def test_z1720(): Unit = doTest()
}
