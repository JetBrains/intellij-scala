import scalaz.syntax.apply._
import scalaz._

class SCL6417_Performance {

  def testValidation(): Unit = {
    (validateOptionalX(Some(0)) |@| validateOptionalY(Some(1)) |@|
      validateOptionalY(Some(1)) |@| validateOptionalY(Some(1)) |@|
      validateOptionalY(Some(1)) |@| validateOptionalY(Some(1)) |@|
      validateOptionalY(Some(1)) |@| validateOptionalY(Some(1)) |@|
      validateOptionalY(Some(1)) |@| validateOptionalY(Some(1)) |@|
      validateOptionalY(Some(1)) |@| validateOptionalY(Some(1))).tupled match {
      case Success(t) =>
        /*start*/t._12/*end*/
        print(t)
      case Failure(errors) => println(errors.toString)
    }
  }

  def validateOptionalX(x: Option[Int]): ValidationNel[String, Int] = x match {
    case Some(p) =>
      if (p < 0) Failure("Errror!").toValidationNel
      else Success(p)
    case None => Success(0)
  }

  def validateOptionalY(y: Option[Int]): ValidationNel[String, Int] = y match {
    case Some(yy) => Success(yy)
    case None => Failure("Required!").toValidationNel
  }

}
//Int