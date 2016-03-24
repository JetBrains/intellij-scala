object SCL5215 extends App {
  val subscribersByType = Map("One" -> new OneWorker,
    "Two" -> new TwoWorker)
  val u: Map[String, Worker[_ >: TwoAuth with OneAuth <: Auth, _ >: TwoAuthService with OneAuthService <: AuthService[_ >: TwoAuth with OneAuth <: Auth]]] =
    /*start*/subscribersByType/*end*/
}

abstract class Worker[A <: Auth, S <: AuthService[A]]
class OneWorker extends Worker[OneAuth, OneAuthService]
class TwoWorker extends Worker[TwoAuth, TwoAuthService]


class AuthService[T <: Auth]
class OneAuthService extends AuthService[OneAuth]
class TwoAuthService extends AuthService[TwoAuth]


class OneAuth extends Auth
class TwoAuth extends Auth
class Auth

//Map[String, Worker[_ >: TwoAuth with OneAuth <: Auth, _ >: TwoAuthService with OneAuthService <: AuthService[_ >: TwoAuth with OneAuth <: Auth]]]