name := "ActorsMap"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

libraryDependencies +=  "com.typesafe.akka" %% "akka-actor" % "2.3.11"
libraryDependencies += "com.github.martincooper" %% "scala-datatable" % "0.7.0"

resolvers ++= Seq(
 // "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "org.scala-saddle" %% "saddle-core" % "1.3.+"
  // (OPTIONAL) "org.scala-saddle" %% "saddle-hdf5" % "1.3.+"
)

/*libraryDependencies += "com.typesafe.akka" % "akka-remote" % "2.0.2"*/