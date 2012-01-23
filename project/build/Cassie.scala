import sbt._
import com.twitter.sbt._
import com.github.olim7t.sbtscalariform._

class Cassie(info: sbt.ProjectInfo) extends StandardParentProject(info)
  with ParentProjectDependencies
  with DefaultRepos
  with SubversionPublisher {

  override def usesMavenStyleBasePatternInPublishLocalConfiguration = true
  override def subversionRepository = Some("https://svn.twitter.biz/maven/")

  val slf4jVersion = "1.5.8"

  val coreProject = project(
    "cassie-core", "cassie-core",
    new CoreProject(_))

  val hadoopProject = project(
    "cassie-hadoop", "cassie-hadoop",
    new HadoopProject(_), coreProject)

  val serversetsProject = project(
    "cassie-serversets", "cassie-serversets",
    new ServerSetsProject(_), coreProject)

  trait Defaults extends StandardLibraryProject with SubversionPublisher with PublishSite 
    with NoisyDependencies with ProjectDependencies with DefaultRepos with ScalariformPlugin {

  }

  class CoreProject(info: ProjectInfo) extends StandardLibraryProject(info) with Defaults
    with CompileThriftFinagle {

    projectDependencies(
      "finagle" ~ "finagle-core",
      "finagle" ~ "finagle-thrift",
      "util"    ~ "util-core",
      "util"    ~ "util-logging"
    )

    val slf4jApi =      "org.slf4j" % "slf4j-api"   % slf4jVersion withSources() intransitive()
    val slf4jBindings = "org.slf4j" % "slf4j-jdk14" % slf4jVersion withSources() intransitive()
    val slf4jNop =      "org.slf4j" %  "slf4j-nop"  % slf4jVersion % "provided"
    val codecs =        "commons-codec" % "commons-codec" % "1.5"

    /**
     * Test Dependencies
     */
    val scalaTest =      "org.scalatest"           % "scalatest_2.8.1"  % "1.5.1" % "test"
    val mockito =        "org.mockito"             % "mockito-all"      % "1.8.5" % "test"
    val junitInterface = "com.novocode"            % "junit-interface"  % "0.7"   % "test->default"
    val scalaCheck =     "org.scala-tools.testing" % "scalacheck_2.8.1" % "1.8"   % "test"

    // Some of the autogenerated java code cause javadoc errors.
    override def docSources = sources(mainScalaSourcePath##)

    override def compileOptions = Deprecation :: Unchecked :: super.compileOptions.toList

    // include test-thrift definitions: see https://github.com/twitter/standard-project/issues#issue/13
    override def thriftSources = super.thriftSources +++ (testSourcePath / "thrift" ##) ** "*.thrift"

    def runExamplesAction = task { args => runTask(Some("com.twitter.cassie.jtests.examples.CassieRun"), testClasspath, args) dependsOn(test) }
    lazy val runExample = runExamplesAction

    override def scalariformOptions = Seq(VerboseScalariform, PreserveDanglingCloseParenthesis(true))
  }

  class HadoopProject(info: ProjectInfo) extends StandardLibraryProject(info) with Defaults {

    val hadoop    = "org.apache.hadoop" % "hadoop-core" % "0.20.2"

    // Some of the autogenerated java code cause javadoc errors.
    override def docSources = sources(mainScalaSourcePath##)
  }

  class ServerSetsProject(info: ProjectInfo) extends StandardLibraryProject(info) with Defaults {

    projectDependencies("finagle" ~ "finagle-serversets")

    override def ivyXML =
      <dependencies>
        <exclude org="jline"/>
        <exclude org="javax.jms"/>
        <exclude org="com.sun.jdmk"/>
        <exclude org="com.sun.jmx"/>
      </dependencies>
  }
}
