package org.jetbrains.plugins.scala.lang.scaladoc

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.psi.{PsiElement, PsiErrorElement}
import org.jetbrains.plugins.scala.corpus.{CorpusProjects, ProjectCorpusTestBase, ProjectCorpusTestDef}
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocSyntaxElement}
import org.junit.{Assert, Test}

import scala.collection.mutable

/**
 * Test trait that verifies ScalaDoc parsing in source files.
 * It traverses all source files in the configured packages and checks that
 * all ScalaDoc comments can be parsed without errors.
 */
trait ScalaDocCorpusTest extends ProjectCorpusTestBase {
  private lazy val config = ScalaDocCorpusTest.configs(projectDef)

  private def psiLocation(e: PsiElement): String = {
    val file = e.getContainingFile
    val offset = e.getTextOffset
    val path = file.getVirtualFile.getPath
    // the start within the jar sources file
    val packagePathBegin = path.indexOf('!') + 1
    val packagePath = path.substring(packagePathBegin)
    s"$packagePath[$offset]"
  }

  @Test
  def testScalaDocParsing(): Unit = {
    // I set this so that the test log is not cluttered with useless log messages
    ApplicationManagerEx.runInStressTest(true, () => {
      val manager = ScalaPsiManager.instance(getProject)

      println("Collecting source files...")

      val sourceFiles = allSources(Set.empty)

      println(s"Found ${sourceFiles.size} source files")

      var totalDocs = 0
      var errorDocs = 0
      val errors = scala.collection.mutable.ArrayBuffer[(String, String)]()
      var expectedErrors = config.ignorePsiErrors

      sourceFiles.foreach { file =>
        val scalaDocComments = file.depthFirst().filterByType[ScDocComment].toSeq

        scalaDocComments.foreach { doc =>
          totalDocs += 1

          val errorElements = doc.depthFirst().filterByType[PsiErrorElement].toSeq
          if (errorElements.nonEmpty) {
            errorDocs += 1

            errorElements.foreach { e =>
              val errorDescriptor = psiLocation(e) -> e.getErrorDescription
              if (expectedErrors.contains(errorDescriptor)) {
                expectedErrors -= errorDescriptor
              } else {
                errors += errorDescriptor
              }
            }
          }
        }
      }

      println(s"Checked $totalDocs ScalaDoc comments, found $errorDocs with errors")

      if (expectedErrors.nonEmpty) {
        val errorReport = expectedErrors.map { case (docId, msg) =>
          s"ScalaDoc at $docId: $msg"
        }.mkString("\n")
        val fail = if (errors.nonEmpty) println(_: String) else Assert.fail(_: String)
        fail(s"Expected errors not found:\n$errorReport")
      }


      if (errors.nonEmpty) {
        val errorReport = errors.map { case (docId, msg) =>
          s"$docId: $msg"
        }.mkString("\n")

        Assert.fail(s"Found ${errors.length} ScalaDoc comments with parsing errors:\n$errorReport")
      }
    })
  }

  private def findLinksByRegex(text: String): Int = {
    val x = raw"\[\[+".r
    var count = 0
    var i = 0
    while (i < text.length) {
      x.findFirstMatchIn(text.subSequence(i, text.length)) match {
        case Some(m) =>
          val end = i + m.end
          val len = m.end - m.start
          text.indexOf("]".repeat(len), end) match {
            case -1 =>
              return count
            case closing =>
              count += 1
              i = closing + 1
          }
        case None =>
          return count
      }
    }
    count
  }

  @Test
  def testLinkRegexMatchesPsiElements(): Unit = {
    // I set this so that the test log is not cluttered with useless log messages
    ApplicationManagerEx.runInStressTest(true, () => {
      println("Collecting source files...")

      val sourceFiles = allSources(Set.empty)

      println(s"Found ${sourceFiles.size} source files")

      var totalRegexMatches = 0
      var totalPsiLinks = 0
      val mismatches = mutable.ArrayBuffer.empty[(String, String)]
      val expectedErrors = config.ignoreLinkRegexMismatch.to(mutable.Map)

      sourceFiles
        .flatMap(_.depthFirst().filterByType[ScDocComment])
        .foreach { docComment =>
          val fileText = docComment.getText

          // Count balanced bracket links using custom parsing
          val regexMatches = findLinksByRegex(fileText)

          val expectedFlags = ScalaDocTokenType.DOC_LINK_TAG.getFlagConst | ScalaDocTokenType.DOC_HTTP_LINK_TAG.getFlagConst
          val psiLinks = docComment.depthFirst()
            .filterByType[ScDocSyntaxElement]
            .filter(e => (e.getFlags & expectedFlags) != 0)
            .count(_.getText.startsWith("[["))

          totalRegexMatches += regexMatches
          totalPsiLinks += psiLinks
          val loc = psiLocation(docComment)
          if (regexMatches != psiLinks) {
            expectedErrors.remove(loc) match {
              case Some((expectedRegexMatches, expectedPsiLinks)) =>
                if (regexMatches != expectedRegexMatches) {
                  mismatches += ((loc, s"Expected $expectedRegexMatches regex matches, got $regexMatches"))
                }
                if (psiLinks != expectedPsiLinks) {
                  mismatches += ((loc, s"Expected $expectedPsiLinks psiLinks, got $psiLinks"))
                }
              case None =>
                mismatches += ((loc, s"regex matches [$regexMatches] != psiLinks [$psiLinks]"))
            }
          }
        }

      println(s"Total regex matches: $totalRegexMatches")
      println(s"Total PSI links: $totalPsiLinks")

      if (mismatches.nonEmpty) {
        val mismatchReport = mismatches.map { case (loc, msg) =>
          s"$loc: $msg"
        }.mkString("\n")

        Assert.fail(s"Found ${mismatches.length} files with mismatched counts:\n$mismatchReport")
      }
    })
  }
}

object ScalaDocCorpusTest {
  case class Config(ignorePsiErrors: Set[(String, String)] = Set.empty,
                    ignoreLinkRegexMismatch: Map[String, (Int, Int)] = Map.empty)


  private def ignoreAllParseErrors[T]: Set[T] = new Set[T] {
    override def incl(elem: T): Set[T] = throw new UnsupportedOperationException
    override def excl(elem: T): Set[T] = this
    override def contains(elem: T): Boolean = true
    override def iterator: Iterator[T] = Iterator.empty
  }

  val configs: Map[ProjectCorpusTestDef, Config] = Map(
    CorpusProjects.Akka.scala2 -> Config(
      ignorePsiErrors = Set(
        "/akka/stream/OverflowStrategy.scala[3557]" -> "Inline tag",
        "/akka/stream/impl/TraversalBuilder.scala[2639]" -> "No closing element",
        "/akka/http/impl/engine/http2/Http2Protocol.scala[8215]" -> "Wiki syntax element closed by new paragraph",
        "/akka/http/impl/engine/http2/Http2Protocol.scala[8493]" -> "No closing element",
        "/akka/http/javadsl/common/PartialApplication.scala[461]" -> "Inline tag",
        "/akka/http/javadsl/common/PartialApplication.scala[522]" -> "Inline tag",
        "/akka/io/UdpManager.scala[759]" -> "Identifier, 'this', or 'package' expected",
        "/akka/pattern/BackoffOptions.scala[2377]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[2394]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[2415]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[2432]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[2469]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[2486]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[5710]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[5727]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[5748]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[5765]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[5802]" -> "Cross tags",
        "/akka/pattern/BackoffOptions.scala[5819]" -> "Cross tags",
        "/akka/parboiled2/util/Base64.scala[3858]" -> "Inline tag",
        "/akka/actor/AbstractActor.scala[8288]" -> "Unknown tag: @Override",

      ),
      ignoreLinkRegexMismatch = Map(
        "/akka/pattern/BackoffOptions.scala[3860]" -> (3, 2),
        "/akka/pattern/BackoffOptions.scala[537]" -> (3, 2),
      ),
    ),
    CorpusProjects.Akka.scala3 -> Config(
      ignorePsiErrors = Set(
        "/akka/io/UdpManager.scala[759]" -> "Identifier, 'this', or 'package' expected"
      ),
      ignoreLinkRegexMismatch = Map(
        // mostly about problems finding links after html tags
        "/akka/http/impl/engine/http2/Http2SettingsHeader.scala[0]" -> (0, 1),
        "/akka/http/impl/util/JavaVersion.scala[0]" -> (0, 1),
        "/akka/actor/Actor.scala[21883]" -> (4, 0),
        "/akka/dispatch/BalancingDispatcher.scala[435]" -> (1, 0),
        "/akka/serialization/Serializer.scala[3481]" -> (11, 4),
        "/akka/serialization/Serializer.scala[566]" -> (9, 2),
        "/akka/event/Logging.scala[9461]" -> (8, 2),
        "/akka/actor/typed/Behavior.scala[3810]" -> (2, 3),
        "/akka/actor/ActorRef.scala[902]" -> (5, 6),
        "/akka/io/Tcp.scala[29164]" -> (3, 4),
        "/akka/actor/typed/scaladsl/ActorContext.scala[13093]" -> (3, 4),
        "/akka/cluster/Cluster.scala[1736]" -> (9, 11),
        "/akka/actor/ActorRef.scala[4567]" -> (1, 2),
        "/akka/cluster/Cluster.scala[15567]" -> (3, 6),
        "/akka/actor/typed/Behavior.scala[3168]" -> (1, 2),

      )
    ),
    CorpusProjects.Cats.scala2 -> Config(
      ignorePsiErrors = Set(
        "/cats/FlatMap.scala[6988]" -> "No closing element",
        "/cats/syntax/flatMap.scala[3174]" -> "No closing element",
        "/cats/data/Ior.scala[1417]" -> "Closing link tag before opening",
        "/cats/data/Ior.scala[1448]" -> "Closing link tag before opening",
        "/cats/data/Ior.scala[1477]" -> "Closing link tag before opening",
        "/cats/effect/IOLocal.scala[7384]" -> "Identifier, 'this', or 'package' expected"
      ),
      ignoreLinkRegexMismatch = Map(
        "/io/circe/numbers/BiggerDecimal.scala[2154]" -> (2, 0),
        "/cats/data/Ior.scala[1245]" -> (13, 6),
        "/cats/data/IndexedReaderWriterStateT.scala[1179]" -> (3, 0),
        "/cats/data/EitherT.scala[24789]" -> (1, 0),
        "/cats/effect/std/Semaphore.scala[3220]" -> (1, 0),
        "/cats/data/EitherT.scala[21277]" -> (4, 1),
        "/cats/data/EitherT.scala[23499]" -> (1, 0),
        "/cats/effect/std/Semaphore.scala[1814]" -> (1, 0),
        "/cats/data/EitherT.scala[24437]" -> (1, 0),
        "/cats/effect/std/Semaphore.scala[1209]" -> (2, 0),
        "/cats/effect/IOLocal.scala[692]" -> (9, 6),
        "/cats/data/EitherT.scala[22459]" -> (1, 0),
        "/cats/effect/std/Semaphore.scala[2589]" -> (1, 0),
        "/cats/effect/std/Semaphore.scala[2926]" -> (1, 0),
        "/cats/data/OptionT.scala[22054]" -> (1, 0),
      )
    ),
    CorpusProjects.Cats.scala3 -> Config(
      ignorePsiErrors = Set(
        "/cats/effect/IOLocal.scala[7384]" -> "Identifier, 'this', or 'package' expected"
      ),
      ignoreLinkRegexMismatch = Map(
        // mostly problems where the link is within inline code alá `[[func]](arg)`
        "/cats/effect/std/Semaphore.scala[3220]" -> (1, 0),
        "/cats/data/OptionT.scala[22054]" -> (1, 0),
        "/cats/effect/std/Semaphore.scala[2926]" -> (1, 0),
        "/cats/effect/std/Semaphore.scala[1209]" -> (2, 0),
        "/cats/data/Ior.scala[1245]" -> (13, 6),
        "/cats/effect/std/Semaphore.scala[1814]" -> (1, 0),
        "/cats/data/EitherT.scala[22459]" -> (1, 0),
        "/cats/data/EitherT.scala[23499]" -> (1, 0),
        "/cats/data/EitherT.scala[24437]" -> (1, 0),
        "/cats/data/EitherT.scala[21277]" -> (4, 1),
        "/cats/data/IndexedReaderWriterStateT.scala[1179]" -> (3, 0),
        "/cats/effect/std/Semaphore.scala[2589]" -> (1, 0),
        "/cats/effect/IOLocal.scala[692]" -> (9, 6),
        "/cats/data/EitherT.scala[24789]" -> (1, 0),
      )
    ),
    CorpusProjects.Circe.scala2 -> Config(
      ignorePsiErrors = Set(
        "/io/circe/numbers/BiggerDecimal.scala[2428]" -> "Cross tags",
        "/io/circe/numbers/BiggerDecimal.scala[2439]" -> "Cross tags",
        "/io/circe/numbers/BiggerDecimal.scala[2508]" -> "No closing element"
      )
    ),
    CorpusProjects.Fs2.scala2 -> Config(
      ignorePsiErrors = Set(
        "/fs2/Stream.scala[8631]" -> "Unknown tag: @hideImplicitConversion",
        "/fs2/Stream.scala[8667]" -> "Unknown tag: @hideImplicitConversion",
      ),
      ignoreLinkRegexMismatch = Map(
        "/fs2/Stream.scala[46501]" -> (1, 0),
        "/fs2/Stream.scala[45141]" -> (1, 0),
        "/fs2/Stream.scala[44355]" -> (1, 0),
        "/fs2/Stream.scala[195954]" -> (1, 0),
        "/fs2/Stream.scala[107615]" -> (1, 0),
        "/fs2/Stream.scala[1826]" -> (3, 2),
        "/fs2/Stream.scala[196309]" -> (1, 0),
        "/fs2/Stream.scala[39533]" -> (1, 0),
      ),
    ),
    CorpusProjects.Fs2.scala3 -> Config(
      ignoreLinkRegexMismatch = Map(
        "/fs2/Stream.scala[46501]" -> (1, 0),
        "/fs2/Stream.scala[45141]" -> (1, 0),
        "/fs2/Stream.scala[44355]" -> (1, 0),
        "/fs2/Stream.scala[195954]" -> (1, 0),
        "/fs2/Stream.scala[107615]" -> (1, 0),
        "/fs2/Stream.scala[1826]" -> (3, 2),
        "/fs2/Stream.scala[196309]" -> (1, 0),
        "/fs2/Stream.scala[39533]" -> (1, 0),
      )
    ),
    CorpusProjects.Jsoniter.scala3 -> Config(
      ignoreLinkRegexMismatch = Map(
        "/com/github/plokhotnyuk/jsoniter_scala/core/ReaderConfig.scala[52]" -> (8, 1),
      )
    ),
    CorpusProjects.Play.scala2 -> Config(
      ignorePsiErrors = Set(
        "/play/api/mvc/Results.scala[16839]" -> "Inline tag",
        "/play/api/mvc/Results.scala[16956]" -> "Inline tag",
        "/play/api/mvc/Results.scala[17097]" -> "Inline tag",
        "/play/api/mvc/Results.scala[17128]" -> "Inline tag",
        "/play/api/mvc/Results.scala[17144]" -> "Inline tag",
        "/play/api/mvc/Results.scala[17383]" -> "Inline tag",
        "/play/api/mvc/Results.scala[19378]" -> "Inline tag",
        "/play/api/mvc/Results.scala[19533]" -> "Inline tag",
        "/play/api/mvc/Results.scala[19612]" -> "Inline tag",
        "/play/api/mvc/Results.scala[19670]" -> "Inline tag",
        "/play/api/mvc/Results.scala[20442]" -> "Inline tag",
        "/play/api/mvc/Results.scala[20597]" -> "Inline tag",
        "/play/api/mvc/Results.scala[20676]" -> "Inline tag",
        "/play/api/mvc/Results.scala[20734]" -> "Inline tag",
        "/play/api/mvc/Results.scala[21790]" -> "Inline tag",
        "/play/api/mvc/Results.scala[21945]" -> "Inline tag",
        "/play/api/mvc/Results.scala[22024]" -> "Inline tag",
        "/play/api/mvc/Results.scala[22082]" -> "Inline tag",
        "/play/api/mvc/Results.scala[24561]" -> "Inline tag",
        "/play/api/mvc/Results.scala[24716]" -> "Inline tag",
        "/play/api/mvc/Results.scala[24795]" -> "Inline tag",
        "/play/api/mvc/Results.scala[24889]" -> "Inline tag",
        "/play/api/mvc/Results.scala[26708]" -> "Inline tag",
        "/play/api/mvc/Results.scala[26863]" -> "Inline tag",
        "/play/api/mvc/Results.scala[26942]" -> "Inline tag",
        "/play/api/mvc/Results.scala[27036]" -> "Inline tag",
        "/play/api/mvc/Results.scala[28133]" -> "Inline tag",
        "/play/api/mvc/Results.scala[28288]" -> "Inline tag",
        "/play/api/mvc/Results.scala[28367]" -> "Inline tag",
        "/play/api/mvc/Results.scala[28425]" -> "Inline tag",
        "/play/core/utils/HttpHeaderEncoding.scala[2844]" -> "Identifier, 'this', or 'package' expected",
        "/play/core/utils/HttpHeaderEncoding.scala[2844]" -> "Expected description or closing link tag",
        "/play/core/utils/HttpHeaderEncoding.scala[3008]" -> "No closing element",
        "/play/core/utils/HttpHeaderEncoding.scala[3305]" -> "Identifier, 'this', or 'package' expected",
        "/play/core/utils/HttpHeaderEncoding.scala[3305]" -> "Expected description or closing link tag",
        "/play/core/utils/HttpHeaderEncoding.scala[3469]" -> "No closing element",
        "/play/api/http/HttpErrorInfo.scala[591]" -> "Inline tag",
        "/play/api/http/HttpErrorInfo.scala[712]" -> "Inline tag",
        "/play/api/http/HttpErrorInfo.scala[795]" -> "Inline tag",
        "/play/api/http/HttpErrorInfo.scala[876]" -> "Inline tag",
      )
    ),
    CorpusProjects.ScalaLibrary.scala3 -> Config(
      ignorePsiErrors = Set(
        "/scala/concurrent/duration/Duration.scala[13938]" -> "Cross tags",
        "/scala/concurrent/duration/Duration.scala[13948]" -> "Cross tags",
        "/scala/language.scala[809]" -> "Cross tags",
        "/scala/language.scala[818]" -> "Cross tags",
        "/scala/language.scala[892]" -> "Cross tags",
        "/scala/language.scala[900]" -> "Cross tags",
        "/scala/language.scala[939]" -> "Cross tags",
        "/scala/language.scala[952]" -> "Cross tags",
        "/scala/language.scala[1026]" -> "Cross tags",
        "/scala/language.scala[1038]" -> "Cross tags",
        "/scala/language.scala[1115]" -> "Cross tags",
        "/scala/language.scala[1135]" -> "Cross tags",
        "/scala/language.scala[1214]" -> "Cross tags",
        "/scala/language.scala[1225]" -> "Cross tags",
        "/scala/language.scala[1311]" -> "Cross tags",
        "/scala/language.scala[1327]" -> "Cross tags",
        "/scala/language.scala[1395]" -> "Cross tags",
        "/scala/language.scala[1408]" -> "Cross tags",
        "/scala/collection/ArrayOps.scala[9532]" -> "Cross tags",
        "/scala/collection/ArrayOps.scala[9549]" -> "Cross tags",
        "/scala/collection/ArrayOps.scala[10248]" -> "Cross tags",
        "/scala/collection/ArrayOps.scala[10267]" -> "Cross tags",
        "/scala/sys/process/ProcessBuilder.scala[1679]" -> "Cross tags",
        "/scala/sys/process/ProcessBuilder.scala[1694]" -> "Cross tags",
        "/scala/Array.scala[24199]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24256]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24310]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24364]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24420]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24475]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24528]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24582]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24635]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24690]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24744]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24812]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24880]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[24951]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[25020]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[25090]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[25159]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[25228]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[25298]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[25370]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/Array.scala[25439]" -> "Unknown tag: @hideImplicitConversion",
        "/scala/util/Using.scala[740]" -> "Cross tags",
        "/scala/util/Using.scala[746]" -> "Cross tags",
        "/scala/util/Using.scala[1232]" -> "Cross tags",
        "/scala/util/Using.scala[1246]" -> "Cross tags",
        "/scala/util/Using.scala[3356]" -> "Cross tags",
        "/scala/util/Using.scala[3371]" -> "Cross tags",
        "/scala/util/Using.scala[4380]" -> "Cross tags",
        "/scala/util/Using.scala[4397]" -> "Cross tags",
        "/scala/util/Using.scala[4475]" -> "Cross tags",
        "/scala/util/Using.scala[4485]" -> "Cross tags",
        "/scala/util/Using.scala[4509]" -> "Cross tags",
        "/scala/util/Using.scala[4527]" -> "Cross tags",
        "/scala/util/Using.scala[5310]" -> "Cross tags",
        "/scala/util/Using.scala[5316]" -> "Cross tags",
        "/scala/util/Using.scala[9233]" -> "Cross tags",
        "/scala/util/Using.scala[9239]" -> "Cross tags",
        "/scala/util/Using.scala[15765]" -> "Cross tags",
        "/scala/util/Using.scala[15771]" -> "Cross tags",
        "/scala/util/Using.scala[16257]" -> "Cross tags",
        "/scala/util/Using.scala[16271]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[777]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[823]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[996]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[1053]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[1316]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[1362]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[1533]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[1589]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[1873]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[1919]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[2172]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[2218]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[2447]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[2493]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[2753]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[2799]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3026]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3072]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3341]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3387]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3552]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3608]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3875]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[3921]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[4215]" -> "Cross tags",
        "/scala/collection/convert/AsJavaExtensions.scala[4261]" -> "Cross tags",
        "/scala/deprecated.scala[1621]" -> "Cross tags",
        "/scala/deprecated.scala[1633]" -> "Unknown tag: @deprecated`",
        "/scala/deprecated.scala[1691]" -> "Wiki syntax element closed by new paragraph",
        "/scala/collection/generic/IsIterable.scala[4348]" -> "Cross tags",
        "/scala/collection/generic/IsIterable.scala[4359]" -> "Cross tags",
        "/scala/jdk/StreamConverters.scala[2173]" -> "Cross tags",
        "/scala/jdk/StreamConverters.scala[2181]" -> "Cross tags",
        "/scala/jdk/StreamConverters.scala[2570]" -> "Cross tags",
        "/scala/jdk/StreamConverters.scala[2578]" -> "Cross tags",
        "/scala/collection/mutable/LongMap.scala[1546]" -> "Wiki syntax element closed by new paragraph",
        "/scala/collection/convert/AsScalaExtensions.scala[797]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[844]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[1111]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[1158]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[1401]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[1448]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[1711]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[1758]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[1995]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[2042]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[2278]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[2325]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[2568]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[2615]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[2925]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[2972]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[3249]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[3296]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[3548]" -> "Cross tags",
        "/scala/collection/convert/AsScalaExtensions.scala[3595]" -> "Cross tags",
        "/scala/collection/mutable/MutationTracker.scala[690]" -> "Cross tags",
        "/scala/collection/mutable/MutationTracker.scala[706]" -> "Cross tags",
        "/scala/collection/mutable/MutationTracker.scala[774]" -> "Cross tags",
        "/scala/collection/mutable/MutationTracker.scala[789]" -> "Cross tags",
        "/scala/collection/mutable/MutationTracker.scala[847]" -> "Cross tags",
        "/scala/collection/mutable/MutationTracker.scala[874]" -> "Cross tags",
        "/scala/math/BigInt.scala[21447]" -> "No closing element",
        "/scala/jdk/Accumulator.scala[1062]" -> "Cross tags",
        "/scala/jdk/Accumulator.scala[1091]" -> "Cross tags"
      ),
      ignoreLinkRegexMismatch = Map(
        "/scala/concurrent/Awaitable.scala[730]" -> (3, 2),
        "/scala/concurrent/duration/Duration.scala[7219]" -> (1, 0),
        "/scala/reflect/Manifest.scala[439]" -> (1, 0),
        "/scala/App.scala[449]" -> (2, 1),
        "/scala/concurrent/Awaitable.scala[1625]" -> (3, 2),
      )
    ),
    CorpusProjects.ScalaLibrary_3_8.scala3 -> Config(
      ignoreLinkRegexMismatch = Map(
        "/scala/typeConstraints.scala[7105]" -> (5, 4),
        "/scala/App.scala[479]" -> (2, 1),
        "/scala/reflect/Manifest.scala[468]" -> (1, 0),
        "/scala/concurrent/duration/Duration.scala[13300]" -> (3, 0),
        "/scala/typeConstraints.scala[7035]" -> (5, 4),
      )
    ),
    CorpusProjects.Scalacheck.scala2 -> Config(
      ignorePsiErrors = Set(
        "/org/scalacheck/Gen.scala[48396]" -> "No closing element"
      )
    ),
    CorpusProjects.Scalactic.scala2 -> Config(
      ignorePsiErrors = Set(
        "/org/scalactic/SetEqualityConstraints.scala[2712]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/SetEqualityConstraints.scala[4479]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TripleEquals.scala[3097]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NumericString.scala[2464]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NumericString.scala[3294]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptyArray.scala[22568]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptyArray.scala[72463]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptyArray.scala[72528]" -> "Unknown tag: @tparm",
        "/org/scalactic/MapEqualityConstraints.scala[2881]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/MapEqualityConstraints.scala[4922]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptyString.scala[3197]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/SeqEqualityConstraints.scala[2628]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/SeqEqualityConstraints.scala[4408]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TraversableEqualityConstraints.scala[2518]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TraversableEqualityConstraints.scala[4487]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptyVector.scala[3166]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptyVector.scala[24313]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptyVector.scala[76939]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptyVector.scala[77004]" -> "Unknown tag: @tparm",
        "/org/scalactic/TypeCheckedTripleEquals.scala[3143]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TypeCheckedTripleEquals.scala[3719]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TypeCheckedTripleEquals.scala[4134]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TypeCheckedTripleEquals.scala[4421]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TypeCheckedTripleEquals.scala[7955]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/TypeCheckedTripleEquals.scala[9666]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptySet.scala[3097]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptySet.scala[17203]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptySet.scala[46207]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptySet.scala[46272]" -> "Unknown tag: @tparm",
        "/org/scalactic/Or.scala[52873]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptyList.scala[3074]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/anyvals/NonEmptyList.scala[28675]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptyList.scala[80261]" -> "Unknown tag: @tparm",
        "/org/scalactic/anyvals/NonEmptyList.scala[80325]" -> "Unknown tag: @tparm",
        "/org/scalactic/Explicitly.scala[3997]" -> "No closing element",
        "/org/scalactic/Explicitly.scala[4339]" -> "No closing element",
        "/org/scalactic/Explicitly.scala[5012]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/Explicitly.scala[5506]" -> "No closing element",
        "/org/scalactic/Explicitly.scala[7413]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/Explicitly.scala[9756]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/Explicitly.scala[10686]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/Explicitly.scala[11684]" -> "Wiki syntax element closed by new paragraph",
        "/org/scalactic/Explicitly.scala[12385]" -> "No closing element",
      ),
      ignoreLinkRegexMismatch = Map(
        "/org/scalactic/FutureSugar.scala[6894]" -> (1, 2),
        "/org/scalactic/FutureSugar.scala[6405]" -> (1, 2),
        "/org/scalactic/TrySugar.scala[5648]" -> (1, 2),
        "/org/scalactic/FutureSugar.scala[741]" -> (3, 4),
        "/org/scalactic/TrySugar.scala[743]" -> (3, 4),
        "/org/scalactic/TrySugar.scala[6528]" -> (1, 2),
      )
    ),
    CorpusProjects.Scalactic.scala3 -> Config(
      ignoreLinkRegexMismatch = Map(
        "/org/scalactic/FutureSugar.scala[741]" -> (3, 1),
        "/org/scalactic/anyvals/NegFiniteFloat.scala[847]" -> (1, 0),
        "/org/scalactic/exceptions/ValidationFailedException.scala[635]" -> (2, 0),
        "/org/scalactic/anyvals/NegFloat.scala[847]" -> (1, 0),
        "/org/scalactic/TrySugar.scala[743]" -> (3, 1),
        "/org/scalactic/TrySugar.scala[6528]" -> (1, 0),
        "/org/scalactic/anyvals/PosFloat.scala[847]" -> (1, 0),
        "/org/scalactic/FutureSugar.scala[6405]" -> (1, 0),
        "/org/scalactic/FutureSugar.scala[6894]" -> (1, 0),
        "/org/scalactic/exceptions/NullArgumentException.scala[635]" -> (1, 0),
        "/org/scalactic/TrySugar.scala[5648]" -> (1, 0),
        "/org/scalactic/anyvals/PosFiniteFloat.scala[847]" -> (1, 0),
      )
    ),
    CorpusProjects.Scalatest.scala2 -> Config(
      ignorePsiErrors = ignoreAllParseErrors,
      ignoreLinkRegexMismatch = Map(
        "/org/scalatest/refspec/RefSpec.scala[822]" -> (1, 0),
        "/org/scalatest/tools/Framework.scala[1557]" -> (5, 0),
        "/org/scalatest/flatspec/FixtureAnyFlatSpec.scala[658]" -> (1, 0),
        "/org/scalatest/wordspec/FixtureAsyncWordSpec.scala[633]" -> (1, 0),
        "/org/scalatest/enablers/WheneverAsserting.scala[757]" -> (2, 1),
        "/org/scalatest/funspec/FixtureAnyFunSpec.scala[656]" -> (1, 0),
        "/org/scalatest/wordspec/AnyWordSpec.scala[674]" -> (1, 0),
        "/org/scalatest/featurespec/FixtureAnyFeatureSpec.scala[660]" -> (1, 0),
        "/org/scalatest/funsuite/FixtureAnyFunSuite.scala[736]" -> (1, 0),
        "/org/scalatest/tools/ScalaTestFramework.scala[1454]" -> (1, 0),
        "/org/scalatest/wordspec/FixtureAnyWordSpec.scala[658]" -> (1, 0),
        "/org/scalatest/prop/Configuration.scala[4965]" -> (3, 2),
        "/org/scalatest/funspec/FixtureAsyncFunSpec.scala[632]" -> (1, 0),
        "/org/scalatest/funsuite/FixtureAsyncFunSuite.scala[767]" -> (1, 0),
        "/org/scalatest/propspec/FixtureAnyPropSpec.scala[672]" -> (1, 0),
        "/org/scalatest/enablers/TableAsserting.scala[1082]" -> (2, 1),
        "/org/scalatest/flatspec/FixtureAsyncFlatSpec.scala[633]" -> (1, 0),
        "/org/scalatest/funsuite/AnyFunSuite.scala[672]" -> (1, 0),
        "/org/scalatest/freespec/FixtureAsyncFreeSpec.scala[633]" -> (1, 0),
        "/org/scalatest/flatspec/AnyFlatSpec.scala[672]" -> (2, 0),
        "/org/scalatest/featurespec/AnyFeatureSpec.scala[660]" -> (1, 0),
        "/org/scalatest/freespec/AnyFreeSpec.scala[672]" -> (1, 0),
        "/org/scalatest/featurespec/FixtureAsyncFeatureSpec.scala[636]" -> (1, 0),
        "/org/scalatest/freespec/FixtureAnyFreeSpec.scala[658]" -> (1, 0),
        "/org/scalatest/funspec/AnyFunSpec.scala[671]" -> (1, 0),
      )
    ),
    CorpusProjects.Scalatest.scala3 -> Config(
      ignorePsiErrors = ignoreAllParseErrors,
      ignoreLinkRegexMismatch = Map(
        "/org/scalatest/refspec/RefSpec.scala[822]" -> (1, 0),
        "/org/scalatest/tools/Framework.scala[1557]" -> (5, 0),
        "/org/scalatest/flatspec/FixtureAnyFlatSpec.scala[658]" -> (1, 0),
        "/org/scalatest/wordspec/FixtureAsyncWordSpec.scala[633]" -> (1, 0),
        "/org/scalatest/enablers/WheneverAsserting.scala[757]" -> (2, 1),
        "/org/scalatest/funspec/FixtureAnyFunSpec.scala[656]" -> (1, 0),
        "/org/scalatest/wordspec/AnyWordSpec.scala[674]" -> (1, 0),
        "/org/scalatest/featurespec/FixtureAnyFeatureSpec.scala[660]" -> (1, 0),
        "/org/scalatest/funsuite/FixtureAnyFunSuite.scala[736]" -> (1, 0),
        "/org/scalatest/tools/ScalaTestFramework.scala[1454]" -> (1, 0),
        "/org/scalatest/wordspec/FixtureAnyWordSpec.scala[658]" -> (1, 0),
        "/org/scalatest/prop/Configuration.scala[4965]" -> (3, 2),
        "/org/scalatest/funspec/FixtureAsyncFunSpec.scala[632]" -> (1, 0),
        "/org/scalatest/funsuite/FixtureAsyncFunSuite.scala[767]" -> (1, 0),
        "/org/scalatest/propspec/FixtureAnyPropSpec.scala[672]" -> (1, 0),
        "/org/scalatest/enablers/TableAsserting.scala[1082]" -> (2, 1),
        "/org/scalatest/flatspec/FixtureAsyncFlatSpec.scala[633]" -> (1, 0),
        "/org/scalatest/funsuite/AnyFunSuite.scala[672]" -> (1, 0),
        "/org/scalatest/freespec/FixtureAsyncFreeSpec.scala[633]" -> (1, 0),
        "/org/scalatest/flatspec/AnyFlatSpec.scala[672]" -> (2, 0),
        "/org/scalatest/featurespec/AnyFeatureSpec.scala[660]" -> (1, 0),
        "/org/scalatest/freespec/AnyFreeSpec.scala[672]" -> (1, 0),
        "/org/scalatest/featurespec/FixtureAsyncFeatureSpec.scala[636]" -> (1, 0),
        "/org/scalatest/freespec/FixtureAnyFreeSpec.scala[658]" -> (1, 0),
        "/org/scalatest/funspec/AnyFunSpec.scala[671]" -> (1, 0),
      )
    ),
    CorpusProjects.Scalaz.scala2 -> Config(
      ignorePsiErrors = Set(
        "/scalaz/Validation.scala[298]" -> "Closing link tag before opening",
        "/scalaz/Order.scala[1111]" -> "Cross tags",
        "/scalaz/Order.scala[1128]" -> "Cross tags",
        "/scalaz/Order.scala[1129]" -> "No closing element",
        "/scalaz/Free.scala[2616]" -> "Unknown tag: @template",
        "/scalaz/Free.scala[2787]" -> "Unknown tag: @template",
        "/scalaz/Free.scala[3015]" -> "Unknown tag: @template",
      ),
      ignoreLinkRegexMismatch = Map(
        "/scalaz/Validation.scala[82]" -> (3, 1)
      )
    ),
    CorpusProjects.Scalaz.scala3 -> Config(
      ignoreLinkRegexMismatch = Map(
        "/scalaz/syntax/std/OptionOps.scala[2206]" -> (1, 0),
        "/scalaz/Validation.scala[82]" -> (3, 2)
      )
    )
  ).withDefaultValue(Config())
}