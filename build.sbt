name := "simple-wator"

version := "1.0"

scalaVersion := "2.11.4"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

val akkaVersion = "2.3.11"

libraryDependencies ++= Seq(
  "org.webjars" % "bootstrap" % "2.3.2",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
)

    