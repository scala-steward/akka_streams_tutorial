package alpakka.file

import alpakka.file.uploader.DirectoryListener
import org.apache.commons.io.FileUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEachTestData, TestData}
import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration.*
import scala.util.Random

/**
  * Designed as IT test on purpose to demonstrate
  * the realistic usage of [[DirectoryListener]], hence we:
  *  - copy files to the file system before each test
  *  - clean up after each test
  *  - have a shared listener instance
  */
final class DirectoryListenerSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEachTestData {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  var listener: DirectoryListener = _
  var tmpRootDir: Path = _
  var parentDir: Path = _
  var processedDir: Path = _

  "DirectoryListener" should {
    "detect_files_on_startup" in {
      listener = DirectoryListener(parentDir, processedDir)
      waitForCondition(2.seconds)(listener.countFilesProcessed() == 2) shouldBe true
    }

    "detect_added_files_at_runtime_in_parent" in {
      copyTestFileToDir(parentDir)
      listener = DirectoryListener(parentDir, processedDir)
      waitForCondition(2.seconds)(listener.countFilesProcessed() == 2 + 1) shouldBe true
    }

    "detect_added_files_at_runtime_in_subdir" in {
      copyTestFileToDir(parentDir.resolve("subdir"))
      listener = DirectoryListener(parentDir, processedDir)
      waitForCondition(2.seconds)(listener.countFilesProcessed() == 2 + 1) shouldBe true
    }

    "detect_added_nested_subdir_at_runtime_with_files_in_subdir" in {
      val tmpDir = Files.createTempDirectory("tmp")
      val sourcePath = Paths.get("src/main/resources/testfile.jpg")
      val targetPath = tmpDir.resolve(createUniqueFileName(sourcePath.getFileName))
      val targetPath2 = tmpDir.resolve(createUniqueFileName(sourcePath.getFileName))
      Files.copy(sourcePath, targetPath)
      Files.copy(sourcePath, targetPath2)

      val targetDir = Files.createDirectories(parentDir.resolve("subdir").resolve("nestedDirWithFiles"))
      FileUtils.copyDirectory(tmpDir.toFile, targetDir.toFile)

      listener = DirectoryListener(parentDir, processedDir)
      waitForCondition(2.seconds)(listener.countFilesProcessed() == 2 + 2) shouldBe true
    }

    "handle invalid parent directory path" in {
      val invalidParentDir = Paths.get("/path/to/non-existent/directory")
      val processedDir = Files.createTempDirectory("processed")

      the[IllegalArgumentException] thrownBy {
        listener = DirectoryListener(invalidParentDir, processedDir)
      } should have message s"Invalid upload directory path: $invalidParentDir"
    }
  }

  override protected def beforeEach(testData: TestData): Unit = {
    logger.info(s"Starting test: ${testData.name}")

    tmpRootDir = Files.createTempDirectory(testData.text)
    logger.info(s"Created tmp dir: $tmpRootDir")

    parentDir = tmpRootDir.resolve("upload")
    processedDir = tmpRootDir.resolve("processed")
    Files.createDirectories(parentDir)
    Files.createDirectories(parentDir.resolve("subdir"))
    Files.createDirectories(processedDir)

    // Populate dirs BEFORE startup
    copyTestFileToDir(tmpRootDir.resolve("upload"))
    copyTestFileToDir(tmpRootDir.resolve("upload/subdir"))
  }

  override protected def afterEach(testData: TestData): Unit = {
    logger.info(s"Cleaning up after test: ${testData.name}")
    listener.stop()
    FileUtils.deleteDirectory(tmpRootDir.toFile)
  }

  private def copyTestFileToDir(target: Path) = {
    val sourcePath = Paths.get("src/main/resources/testfile.jpg")
    val targetPath = target.resolve(createUniqueFileName(createUniqueFileName(sourcePath.getFileName)))
    Files.copy(sourcePath, targetPath)
  }

  private def createUniqueFileName(fileName: Path) = {
    val parts = fileName.toString.split('.').map(_.trim)
    Paths.get(s"${parts.head}${Random.nextInt()}.${parts.reverse.head}")
  }

  private def waitForCondition(maxDuration: FiniteDuration)(condition: => Boolean): Boolean = {
    val startTime = System.currentTimeMillis()
    var elapsed = 0.millis

    while (!condition && elapsed < maxDuration) {
      Thread.sleep(100)
      elapsed = (System.currentTimeMillis() - startTime).millis
    }

    condition
  }
}
