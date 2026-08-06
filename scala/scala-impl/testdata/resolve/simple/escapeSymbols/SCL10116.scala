object SCL10116 {
  val policy =
    s"""
       |{
       |    "conditions": [
       |        ["starts-with", "$$Content-Type", "image/*"],
       |    ]
       |}
  """.<ref>stripMargin
}