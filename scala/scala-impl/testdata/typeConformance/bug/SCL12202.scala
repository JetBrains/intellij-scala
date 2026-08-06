import java.io
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.time.LocalDateTime

type Bound = ChronoLocalDateTime[_ <: ChronoLocalDate] with String

val x: Comparable[_ >: Bound <: Comparable[_ >: Bound]] with io.Serializable =
  if (true) LocalDateTime.now else "string"
//true
