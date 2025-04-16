import scala.util.Try

// Represents https://www.jetbrains.com/help/teamcity/parallel-tests.html#custom-tests
case class TeamCityTestExcludesFileContent(
  algorithm: Option[String],
  current_batch: Int,
  total_batches: Int,
  excludedTests: Set[String],
)

object TeamCityTestExcludesFileContent {
  val SupportedVersion: Int = 1

  def parse(content: String): Either[String, TeamCityTestExcludesFileContent] = {
    var algorithm: Option[String] = None
    var current_batch: Option[Int] = None
    var total_batches: Option[Int] = None
    val excludedTests = Set.newBuilder[String]
    var readingExcludedTests = false

    content.linesIterator.foreach { line =>
      line.split('=').toSeq match {
        case _ if line.startsWith("#") && readingExcludedTests =>
          return Left(s"Didn't expect a commented line after suite=. Line was: $line")
        case Seq("#version", version) =>
          if (version != SupportedVersion.toString) {
            return Left(s"Unsupported version: $version")
          }
        case Seq("#algorithm", algorithmName) =>
          algorithm = Some(algorithmName)
        case Seq("#current_batch", currentBatchStr) =>
          current_batch = Some(
            Try(currentBatchStr.toInt)
              .getOrElse(return Left(s"Invalid line with current_batch: $currentBatchStr"))
          )
        case Seq("#total_batches", totalBatchesStr) =>
          total_batches = Some(
            Try(totalBatchesStr.toInt)
              .getOrElse(return Left(s"Invalid line with total_batches: $totalBatchesStr"))
          )
        case Seq("#suite") =>
          readingExcludedTests = true
        case Seq(testName) =>
          if (readingExcludedTests) {
            if (line.startsWith("#")) {
              return Left(s"Didn't expect a commented line after suite=. Line was: $line")
            }
            excludedTests += testName
          } else {
            return Left(s"Unexpected line: $line")
          }
      }
    }

    Right(TeamCityTestExcludesFileContent(
      algorithm,
      current_batch.getOrElse(return Left("current_batch is not defined")),
      total_batches.getOrElse(return Left("total_batches is not defined")),
      excludedTests.result(),
    ))
  }
}