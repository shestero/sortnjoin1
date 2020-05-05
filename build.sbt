name := "sortnjoin1"

version := "0.1"

scalaVersion := "2.13.2"

lazy val akkaver =  "2.6.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % akkaver,
  "com.typesafe.akka" %% "akka-stream"  % akkaver
)