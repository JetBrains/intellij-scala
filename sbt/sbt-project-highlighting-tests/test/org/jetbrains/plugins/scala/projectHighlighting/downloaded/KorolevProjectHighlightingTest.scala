package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class KorolevProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  //version 1.5.0
  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("fomkin", "korolev", "7beca1ccc1d5fb676ec12b6ca2d43826743f7b56")

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    // The akka-http `Route` type alias doesn't resolve in type position, so every route built from
    // `AkkaHttpService = AkkaHttpServerConfig => Route` is typed as `Any` in the files below.
    "interop/akka/src/main/scala/korolev/akka/package.scala" -> Set(
      (1599, 1604), // Cannot resolve symbol Route
      (2370, 2371), // Cannot resolve symbol ~
      (2721, 2726), // Cannot resolve symbol Route
      (4990, 4995), // Cannot resolve symbol Route
    ),
    "interop/akka/src/main/scala/korolev/akka/SimpleAkkaHttpKorolevApp.scala" -> Set(
      (1078, 1083), // Type mismatch, expected: Flow[HttpRequest, HttpResponse, ?], actual: Any
    ),
    "examples/akka-http/src/main/scala/AkkaHttpExample.scala" -> Set(
      (949, 954), // Type mismatch, expected: Flow[HttpRequest, HttpResponse, ?], actual: Any
    ),
    "examples/cats/src/main/scala/CatsIOExample.scala" -> Set(
      (4149, 4154), // Type mismatch, expected: Flow[HttpRequest, HttpResponse, ?], actual: Any
    ),
    "misc/integration-tests/src/main/scala/gp/GuineaPigRunner.scala" -> Set(
      (2429, 2434), // Type mismatch, expected: Flow[HttpRequest, HttpResponse, Any], actual: Any
    ),
    "interop/zio-http/src/main/scala/korolev/zio/http/ZioHttpKorolev.scala" -> Set(
      // `Http.collectZIO { case req => ... }`: the request type is inferred from the expected result type by scalac,
      // but stays an unbound type parameter here
      (1519, 1522), // Cannot resolve symbol url
      (1536, 1539), // Type mismatch, expected: Request, actual: A
      // zio-http `Path.copy` is public, the module is compiled by Scala 2.13.8 with -Xsource:3
      (2651, 2655), // Symbol copy is inaccessible from this place
    ),
    "modules/korolev/src/main/scala/korolev/Context.scala" -> Set(
      // `new MappedAccess[F, SN, S2, E](this, lens)` inside `MappedAccess[F, S1, SN, E]`: `this` conforms to the
      // expected `Access[F, SN, E]`, but the type parameters are substituted twice
      (12238, 12242), // Type mismatch, expected: Access[F, S2, E], actual: MappedAccess[F, S1, SN, E]
    ),
  )
}
