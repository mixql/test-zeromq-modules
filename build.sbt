val scala3Version = "3.2.0"

lazy val `zio-server-main` = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    run / fork := true,
    name := "server-main",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    Compile / PB.targets := Seq(
      scalapb.gen(grpc = true) -> (Compile / sourceManaged).value,
    ),
    libraryDependencies ++= {
      val vScallop = "4.1.0"
      Seq(
        "org.rogach" %% "scallop" % vScallop,
        "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
        "com.typesafe" % "config" % "1.4.2",
        "org.scalameta" %% "munit" % "0.7.29" % Test,
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0",
        "org.zeromq" % "jeromq" % "0.5.2"
      )
    }
  )

lazy val `ZHelpersLib` = project
  .in(file("zhelpers-lib"))

lazy val `remoteClientScala3` = project
  .in(file("module-scala3")).dependsOn(`ZHelpersLib`)

lazy val stageAll = taskKey[Unit]("Stage all projects")
lazy val prePackArchive = taskKey[Unit]("Prepare project before making tar.gz")
lazy val packArchive = taskKey[Unit]("Making release tar.gz")
lazy val runApp = taskKey[Unit]("run app")
lazy val makeTarGZ = taskKey[Unit]("Pack target dist tar.gz")
lazy val runClientApp = taskKey[Unit]("run client app")

val projects_stage = ScopeFilter(inProjects(`zio-server-main`, `remoteClientScala3`), inConfigurations(Universal))

stageAll := {
  stage.all(projects_stage).value
}

runApp := Def.sequential(stageAll, prePackArchive, runClientApp).value

packArchive := Def.sequential(stageAll, prePackArchive, makeTarGZ).value

prePackArchive := {
  implicit val log = streams.value.log
  val targetStageDir = (`zio-server-main` / baseDirectory).value / "target" / "universal" / "stage"

  copyStageModule(targetStageDir, (remoteClientScala3 / baseDirectory).value / "target" / "universal" / "stage")
}

makeTarGZ := {
  //TO-DO
}

import sbt.internal.util.ManagedLogger

def copyStageModule(targetStageDir: File, sourceStageDir: File)(implicit log: ManagedLogger) = {
  val targetStageBin = targetStageDir / "bin"
  val targetStageLib = targetStageDir / "lib"

  val sourceStageLib = sourceStageDir / "lib"
  val sourceStageBin = sourceStageDir / "bin"
  log.info(s"Copying libs dir ${sourceStageLib.getAbsolutePath} to ${
    targetStageLib.getAbsolutePath
  }")
  IO.copyDirectory(sourceStageLib, targetStageLib)
  log.info(s"Copying bin dir ${sourceStageBin.getAbsolutePath} to ${
    targetStageBin.getAbsolutePath
  }")
  IO.copyDirectory(sourceStageBin, targetStageBin)
}

runClientApp := {
  implicit val log = streams.value.log
  val targetStageDir = (`zio-server-main` / baseDirectory).value / "target" / "universal" / "stage" / "bin"
  val res = runCmdNoWait(Some("server-main.bat"), Some("server-main"), targetStageDir )
  res.exitValue()
}

def runCmd(cmdWindows: String, cmdUnix: String, path: File)
          (implicit log: ManagedLogger): String = {
  import scala.sys.process._
  val isWindows: Boolean = System.getProperty("os.name").toLowerCase().contains("win")
  try {
    if (isWindows) {
      Process("cmd /c " + cmdWindows, path) !!
    } else {
      Process("bash -c " + cmdUnix, path) !!
    }
  } catch {
    case ex: Throwable =>
      log.error(s"Could not execute command ${
        if (isWindows) cmdWindows
        else cmdUnix
      } in path ${path.getAbsolutePath} directory\n [exception]: " + ex.getMessage)
      throw ex
  }
}

  def runCmdNoWait(cmdWindows: Option[String], cmdUnix: Option[String], path: File): scala.sys.process.Process = {
    import scala.sys.process._
    val isWindows: Boolean = System.getProperty("os.name").toLowerCase().contains("win")
    try {
      if (isWindows && cmdWindows.nonEmpty) {
        Process("cmd /c " + cmdWindows.get, path).run()
      } else {
        if (cmdUnix.nonEmpty) 
          Process("bash -c " + cmdUnix.get, path).run()
        else throw new Exception("No cmd is provided")
      }
    } catch {
      case ex: Throwable =>
        println(s"Error: Could not execute command ${
          if (isWindows && cmdWindows.nonEmpty) cmdWindows.get
          else if (cmdUnix.nonEmpty) cmdUnix.get else ""
        } in path ${path.getAbsolutePath} directory\n [exception]: " + ex.getMessage)
        throw ex
    }
  }



