name := "akka-cluster-custom-downing"

version := "0.0.1"

organization := "github.com/TanUkkii007"

homepage := Some(url("https://github.com/TanUkkii007/akka-cluster-custom-downing"))

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-encoding", "UTF-8",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-language:higherKinds"
)

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

val akkaVersion = "2.4.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
//  "com.typesafe.akka" %% "akka-cluster" % akkaVersion  % "test" classifier "tests",
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test",
//  "com.typesafe.akka" %% "akka-testkit" % akkaVersion  % "test" classifier "tests",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)

compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test)

parallelExecution in Test := false

executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
  case (testResults, multiNodeResults) =>
    val overall =
      if (testResults.overall.id < multiNodeResults.overall.id)
        multiNodeResults.overall
      else
        testResults.overall
    Tests.Output(overall,
      testResults.events ++ multiNodeResults.events,
      testResults.summaries ++ multiNodeResults.summaries)
}

configs(MultiJvm)

BintrayPlugin.autoImport.bintrayPackage := "akka-cluster-custom-downing"

enablePlugins(BintrayPlugin, ReleasePlugin)