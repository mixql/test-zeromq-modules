enablePlugins(JavaAppPackaging)

run / fork := true

name := "zhelpers-lib"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.10"

libraryDependencies ++= {
  Seq(
    "org.zeromq" % "jeromq" % "0.5.2"
  )
}