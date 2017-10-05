object Application {

  val jsonData: JsValue = Json.parse( """{"name": "Bob"}""")

  val foo = jsonData.validate[SomeModel] match {
    case s: JsSuccess[someModel] => { s.value.<ref>copy(name = "Test") }
  }

  case class SomeModel(id: Int, name: String)
  implicit val sampleReads: Reads[SomeModel] = ???
}