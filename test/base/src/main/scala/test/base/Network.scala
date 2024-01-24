package test.base

import java.net.Socket
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable
import scala.util.{Random, Try}

trait Network {
  private lazy val minPort = 16384
  private lazy val maxPort = 65536

  def freePort(id: String): Int =
    Network.ports.synchronized {
      Network.ports.getOrElseUpdate(id, claimPort(id))
    }

  private def claimPort(id: String): Int =
    LazyList.continually(Network.random.between(minPort, maxPort)).take(maxPort - minPort).find { port =>
      // Consider an available port to be exclusively acquired if a lock file was newly atomically created
      val lockFile = Network.lockDirectory.resolve(f"port-$port%05d.lock").toFile
      lockFile.createNewFile() && portAvailable(port)
    }.getOrElse(throw new IllegalStateException("No available ports found"))

  private def portAvailable(port: Int): Boolean =
    Try(new Socket("localhost", port)).map(socket => Try(socket.close())).isFailure
}

object Network {
  private val targetDirectoryProperty = "project.target"
  private val targetDirectoryDefault = "target"
  private val ports = mutable.HashMap[String, Int]()
  private val random = new Random()

  private lazy val lockDirectory: Path = {
    val targetDir = Paths.get(Option(System.getProperty(targetDirectoryProperty)).getOrElse(targetDirectoryDefault))
    if (!Files.exists(targetDir)) {
      throw new IllegalStateException(s"Target directory does not exist: $targetDir")
    }
    val lockDir = targetDir.resolve("lock")
    Files.createDirectories(lockDir)
    lockDir
  }
}
