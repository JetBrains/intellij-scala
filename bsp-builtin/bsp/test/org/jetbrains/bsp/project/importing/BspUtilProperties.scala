package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp.testkit.gen.UtilGenerators.genUri
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.plugins.scala.SlowTests
import org.junit.Test
import org.junit.experimental.categories.Category
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalatestplus.scalacheck.Checkers

import java.nio.file.{InvalidPathException, Path, Paths}

@Category(Array(classOf[SlowTests]))
class BspUtilProperties extends Checkers {

  implicit val generatorConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(sizeRange = 20)

  @Test
  def stringOpsToUri(): Unit = check(
    forAll(genUri) { uri =>
      uri.toURI.toString == uri
    }
  )

  @Test
  def uriOpsToFile(): Unit = {
    // Copied with added InvalidPathException fix from `ch.epfl.scala.bsp.testkit.gen.UtilGenerators.genPath`
    val genPath: Gen[Path] = (for {
      segmentCount <- Gen.choose(0, 10)
      segments <- Gen.listOfN(segmentCount, Gen.alphaNumStr)
    } yield {
      val combined = segments.foldLeft("") { (combined, seg) =>
        if (combined.length + seg.length + 1 > 100) combined
        else combined + "/" + seg
      }

      try Some(Paths.get(combined).toAbsolutePath)
      catch {
        case _: InvalidPathException if SystemInfo.isWindows => None
      }
    }).filter(_.nonEmpty).map(_.get)

    check(
      forAll(genPath) { path =>
        path.toUri.toFile == path.toFile
      }
    )
  }
}
