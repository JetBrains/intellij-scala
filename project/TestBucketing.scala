import java.io.File

/**
 * Class bucketing shared by the Community and Ultimate build definitions.
 */
object TestBucketing {
  private val TotalVariable = "TC_SBT_TEST_BUCKETS_TOTAL"
  private val CurrentVariable = "TC_SBT_TEST_BUCKETS_CURRENT"

  private lazy val allocation: Either[String, TestBucketAllocation] =
    TestBucketAllocation.load(TestBucketAllocation.fileIn(new File(".")))

  /**
   * A validated bucket in a partition of test classes.
   *
   * @param totalBuckets positive number of buckets in the partition
   * @param currentBucket zero-based index, smaller than the total number of buckets
   */
  final case class Bucket(totalBuckets: Int, currentBucket: Int) {
    require(totalBuckets > 0, s"$TotalVariable must be greater than zero, got $totalBuckets.")
    require(currentBucket >= 0 && currentBucket < totalBuckets,
      s"$CurrentVariable must be between 0 (inclusive) and $TotalVariable=$totalBuckets (exclusive), got $currentBucket.")

    private lazy val bucketAllocation: Option[TestBucketAllocation] = allocation match {
      case Right(allocation) if allocation.bucketCount != totalBuckets =>
        println(s"[warn] Ignoring ${TestBucketAllocation.FileName}: it contains ${allocation.bucketCount} buckets, " +
          s"but this build uses $totalBuckets. Falling back to name hashes.")
        None
      case Right(allocation) =>
        println(s"[info] Using test bucket allocation (${allocation.bucketCount} buckets, ${allocation.assignments.size} tests)")
        Some(allocation)
      case Left(err) =>
        println(s"[warn] Ignoring test bucket allocation: $err")
        None
    }

    /**
     * Selects classes using recorded allocations, falling back to normalized name hashes.
     *
     * @param testClassName fully qualified name of the discovered test class
     * @return whether the class belongs to this bucket
     */
    def matches(testClassName: String): Boolean = {
      val bucket = bucketAllocation
        .flatMap(_.bucket(testClassName, totalBuckets))
        .getOrElse {
          val hash = testClassName.##
          Math.floorMod(hash, totalBuckets)
        }
      bucket == currentBucket
    }
  }

  /**
   * TC_SBT_TEST_BUCKETS_TOTAL and TC_SBT_TEST_BUCKETS_CURRENT must both be absent
   * (unbucketed) or supplied together.
   * The current bucket is zero-based and must be smaller than the positive total.
   */
  def fromEnvironment(environment: Map[String, String]): Either[String, Option[Bucket]] =
    (environment.get(TotalVariable), environment.get(CurrentVariable)) match {
      case (None, None) => Right(None)
      case (Some(totalValue), Some(currentValue)) =>
        parseBucket(totalValue, currentValue).map(Some(_))
      case _ =>
        Left(s"$TotalVariable and $CurrentVariable must either both be set or both be absent.")
    }

  private def parseBucket(totalValue: String, currentValue: String): Either[String, Bucket] =
    for {
      total <- parseInt(TotalVariable, totalValue)
      current <- parseInt(CurrentVariable, currentValue)
      bucket <-
        if (total <= 0)
          Left(s"$TotalVariable must be greater than zero, got $total.")
        else if (current < 0 || current >= total)
          Left(s"$CurrentVariable must be between 0 (inclusive) and $TotalVariable=$total (exclusive), got $current.")
        else
          Right(Bucket(total, current))
    } yield bucket

  private def parseInt(variable: String, value: String): Either[String, Int] =
    try Right(value.toInt)
    catch {
      case _: NumberFormatException => Left(s"$variable must be a 32-bit integer, got '$value'.")
    }
}
