scalaVersion := "2.13.18"

val blockDuringLoad = taskKey[Unit]("blockDuringLoad")

// Regression scenario for SCL-25058: onLoad schedules a task that blocks forever, so the sbt shell never
// becomes ready. Interrupting a task scheduled during load makes sbt print the interactive project loading failure prompt.
onLoad in Global := ((s: State) => {
  s.log.info("onLoad...")
  "blockDuringLoad" :: s
}) compose (onLoad in Global).value

lazy val root = (project in file("."))
  .settings(
    blockDuringLoad := {
      val log = sbt.Keys.streams.value.log
      log.info("LOAD_STARTED")
      // Block forever. The shell destroy interrupts this, which aborts the load and makes sbt print the interactive load-failure prompt.
      while (true) Thread.sleep(50)
    }
  )
