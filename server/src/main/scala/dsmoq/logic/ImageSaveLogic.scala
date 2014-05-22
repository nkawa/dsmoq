package dsmoq.logic

import org.scalatra.servlet.FileItem
import java.nio.file.Paths
import javax.imageio.ImageIO
import dsmoq.AppConf
import java.awt.image.BufferedImage

object ImageSaveLogic {
  val defaultFileName = "original"
  val imageSizes = Array(16, 32, 48, 128)

  def writeImageFile(imageId: String, file: FileItem) {
    // 拡張子判定(現状例外スロー)
    val fileType = file.name.split('.').last.toLowerCase
    if (!ImageIO.getWriterFormatNames.contains(fileType)) {
      throw new RuntimeException("file format error.")
    }

    val imageDir = Paths.get(AppConf.imageDir, imageId).toFile
    if (!imageDir.exists()) imageDir.mkdir()

    // オリジナル画像の保存
    file.write(imageDir.toPath.resolve(defaultFileName).toFile)

    val bufferedImage = ImageIO.read(file.getInputStream)
    // サムネイル画像の保存
    imageSizes.map { size =>
      val thumbBufferedImage = new BufferedImage(size, size, bufferedImage.getType)
      thumbBufferedImage.getGraphics.drawImage(bufferedImage.getScaledInstance(size, size,
        java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, size, size, null)
      ImageIO.write(thumbBufferedImage, fileType, (imageDir.toPath.resolve(size.toString).toFile))
    }
  }
}
