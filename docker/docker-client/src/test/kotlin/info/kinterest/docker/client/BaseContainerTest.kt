package info.kinterest.docker.client

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.DockerCmdExecFactory
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.ExecStartResultCallback
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import info.kinterest.functional.Try
import info.kinterest.functional.getOrDefault
import info.kinterest.functional.getOrElse
import mu.KotlinLogging
import org.junit.jupiter.api.*
import strikt.api.expectThat
import strikt.assertions.isTrue
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled
class BaseContainerTest {
    val log = KotlinLogging.logger { }
    lateinit var client : DockerClient
    var networks : Set<String> = emptySet()
    var containers : Set<String> = emptySet()

    @BeforeAll
    fun setUp() {
        val cmds: DockerCmdExecFactory = NettyDockerCmdExecFactory()
        val cfg = DockerClientConfigProvider.config()
        client = DockerClientBuilder.getInstance(cfg)
                .withDockerCmdExecFactory(cmds)
                .build()
    }


    @AfterAll
    fun tearDown() {
        containers.forEach {
            Try { client.stopContainerCmd(it).exec(); }.getOrElse { log.warn(it) {} }
            Try {client.removeContainerCmd(it).withRemoveVolumes(true).withForce(true).exec() }.getOrElse { log.warn(it) {} }
        }

        networks.forEach {
            Try {client.removeNetworkCmd(it).exec()}.getOrElse { log.warn(it) {} }
        }
    }

    @Test
    @Disabled
    fun withPull() {
        client.listImagesCmd().withImageNameFilter("hello-world").exec().forEach {
            if(it.repoTags.any { it=="hello-world:latest" }) {
                client.removeImageCmd(it.id).withForce(true)
            }
        }

        val container = BaseContainer(client, "hello-world")
        containers = containers + container.container

        container.start(LogWaitStrategy(Duration.ofSeconds(25), LogAcceptor.string("https://docs.docker.com/get-started/")))
    }

    @Test
    @Disabled
    fun hazelcastClusterNoNw() {
        val container1 = BaseContainer(client, "hazelcast/hazelcast", env = listOf("JAVA_OPTS=-Dhazelcast.config=/opt/cluster/hazelcast-cluster.xml"),
                binds = listOf("/opt/cluster" to listOf(javaClass.classLoader.getResource("hazelcast-cluster.xml"))))
        containers = containers + container1.container
        val container2 = BaseContainer(client, "hazelcast/hazelcast")
        containers = containers + container2.container
        val timeout = Duration.ofSeconds(25)
        container1.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container2.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        val lcb = object : LogContainerResultCallback() {
            override fun onNext(item: Frame?) {
                log.info { "$item" }
            }
        }
        log.info { "Logging container1 {${container1.container}}:" }
        client.logContainerCmd(container1.container).withFollowStream(false).withStdOut(true).withStdErr(true).exec(lcb)
        lcb.awaitCompletion()
        log.info { "Logging container2 {${container2.container}}:" }
        client.logContainerCmd(container2.container).withFollowStream(false).withStdOut(true).withStdErr(true).exec(lcb)
        lcb.awaitCompletion()
    }

    @Test
    @Disabled
    fun hazelcastNetworkNoCluster() {
        val suffix = Random(System.currentTimeMillis()).nextInt(55555)
        val nw1 = client.createNetworkCmd().withDriver("bridge").withName("nw1$suffix").exec().id
        val nw2 = client.createNetworkCmd().withDriver("bridge").withName("nw2$suffix").exec().id
        networks = networks + listOf(nw1, nw2)
        log.info { "nw1: $nw1 nw2: $nw2" }
        val container1 = BaseContainer(client, "hazelcast/hazelcast", network = nw1)
        val container2 = BaseContainer(client, "hazelcast/hazelcast", network = nw2, binds = listOf("/opt/cluster" to listOf(javaClass.classLoader.getResource("hazelcast-cluster.xml"))))
        //container1.copyResourceToContainer("hazelcast-cluster.xml", javaClass.classLoader.getResource("hazelcast-cluster.xml"), "/opt/hazelcast/")

        val timeout = Duration.ofSeconds(25)
        container1.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        containers = containers + container1.container
        container2.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        containers = containers + container2.container

        var res = ""
        val execcb = object : ExecStartResultCallback() {
            override fun onNext(frame: Frame?) {
                res += frame.toString()
                res += "\n"
            }
        }

        val cmdLsId = client.execCreateCmd(container2.container).withAttachStdout(true).withAttachStderr(true).withCmd("ls -l", "/opt/cluster/").exec().id
        res = ""
        client.execStartCmd(container2.container).withExecId(cmdLsId).exec(execcb)
        execcb.awaitCompletion()
        log.info { "ls returned:\n$res" }

        val cmdCatId = client.execCreateCmd(container2.container).withAttachStdout(true).withAttachStderr(true).withCmd("cat", "/opt/cluster/hazelcast-cluster.xml").exec().id
        res = ""

        client.execStartCmd(container2.container).withExecId(cmdCatId).exec(execcb)
        execcb.awaitCompletion()
        log.info { "cat returned:\n$res" }
    }

    @Test
    @Disabled
    fun hazelCastCpCluster() {
        val suffix = Random(System.currentTimeMillis()).nextInt(55555)
        val nw1 = client.createNetworkCmd().withDriver("bridge").withName("nw1$suffix").exec().id.apply { networks = networks + this }
        fun createContainer() = BaseContainer(client, "hazelcast/hazelcast", network = nw1,
                binds = listOf("/opt/cluster" to listOf(javaClass.classLoader.getResource("hazelcast-cluster.xml"))),
                env = listOf("JAVA_OPTS=-Dhazelcast.config=/opt/cluster/hazelcast-cluster.xml"))
                .apply { containers = containers + container }
        val c1 = createContainer()
        val c2 = createContainer()
        val c3 = createContainer()

        val timeout = Duration.ofSeconds(25L)
        fun start(c:BaseContainer) = c.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        start(c1)
        start(c2)
        start(c3)

        logContainer(c1)
        c1.waitForLog(LogAcceptor.regex(".*CPMember.*uuid=.*, address=.*:.*- LEADER.*".toRegex()), timeout)
    }

    @Test
    @Disabled
    fun hazelcastNetworkTwoCluster() {
        val nw1 = client.createNetworkCmd().withDriver("bridge").withName("nw1").exec().id
        val nw2 = client.createNetworkCmd().withDriver("bridge").withName("nw2").exec().id
        networks = networks + listOf(nw1, nw2)
        log.info { "nw1: $nw1 nw2: $nw2" }
        val container1 = BaseContainer(client, "hazelcast/hazelcast", network = nw1)
        containers = containers + container1.container
        val container2 = BaseContainer(client, "hazelcast/hazelcast", network = nw1)
        containers = containers + container2.container
        val container3 = BaseContainer(client, "hazelcast/hazelcast", network = nw2)
        containers = containers + container3.container
        val container4 = BaseContainer(client, "hazelcast/hazelcast", network = nw2)
        containers = containers + container4.container
        val timeout = Duration.ofSeconds(25)
        container1.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container2.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container3.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))
        container4.start(LogWaitStrategy(timeout, LogAcceptor.string("is STARTED")))

        logContainer(container1)
        logContainer(container2)
        logContainer(container3)
        logContainer(container4)
    }

    fun logContainer(container: BaseContainer) {
        val lcb = object : LogContainerResultCallback() {
            override fun onNext(item: Frame?) {
                log.info { "$item" }
            }
        }

        log.info { "Logging container1 {${container.container}}:" }
        client.logContainerCmd(container.container).withFollowStream(false).withStdOut(true).withStdErr(true).exec(lcb)
        lcb.awaitCompletion()
    }

    @Test
    @Disabled
    fun execTest() {
        val container1 = BaseContainer(client, "hazelcast/hazelcast")
        containers = containers + container1.container
        container1.start(LogWaitStrategy(Duration.ofSeconds(25), LogAcceptor.string("is STARTED")))
        val res = container1.exec(listOf("ls", "/opt/"), duration = Duration.of(5, ChronoUnit.SECONDS))
        expectThat(res.isSuccess).isTrue()
        expectThat(res.getOrDefault { listOf() }.any { "hazelcast" in it }).isTrue()
    }
}
