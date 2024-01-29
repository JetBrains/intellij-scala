name := {
  println("[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038")
  "twoLinkedProjects"
}
version := "SNAPSHOT"
scalaVersion := "2.13.5"