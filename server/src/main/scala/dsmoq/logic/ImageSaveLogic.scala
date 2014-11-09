package dsmoq.logic

import org.scalatra.servlet.FileItem
import java.nio.file.Paths
import javax.imageio.ImageIO
import dsmoq.AppConf
import java.awt.image.BufferedImage

object ImageSaveLogic {
  val defaultFileName = "original"
  val uploadPath = "upload"
  val imageSizes = Array(16, 32, 48, 64, 92, 128)

  def writeImageFile(imageId: String, file: FileItem): String = {
    // 拡張子判定(現状例外スロー)
    val fileType = file.name.split('.').last.toLowerCase
    if (!ImageIO.getWriterFormatNames.contains(fileType)) {
      throw new RuntimeException("file format error.")
    }

    val presetImageDir = Paths.get(AppConf.imageDir, uploadPath).toFile
    if (!presetImageDir.exists()) presetImageDir.mkdir()

    val imageDir = presetImageDir.toPath.resolve(imageId).toFile
    if (!imageDir.exists()) imageDir.mkdir()

    // オリジナル画像の保存
    file.write(imageDir.toPath.resolve(defaultFileName).toFile)

    val bufferedImage = ImageIO.read(file.getInputStream)

    // サムネイル画像のwidth, height計算
    val resizeScaleMap = calcResizeScale(bufferedImage.getWidth, bufferedImage.getHeight)
    // サムネイル画像の保存
    imageSizes.map { size =>
      val scale = resizeScaleMap(size)
      val thumbBufferedImage = new BufferedImage(scale._1, scale._2, bufferedImage.getType)
      thumbBufferedImage.getGraphics.drawImage(bufferedImage.getScaledInstance(scale._1, scale._2,
        java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, scale._1, scale._2, null)
      ImageIO.write(thumbBufferedImage, fileType, (imageDir.toPath.resolve(size.toString).toFile))
    }

    "/" + uploadPath + "/" + imageId
  }

  def calcResizeScale(width: Int, height: Int) = {
    imageSizes.map{size =>
      if (width > height) {
        val resizeHeight = (height * (size.toDouble / width)).toInt
        (size, (size, if (resizeHeight == 0) 1 else resizeHeight))
      } else {
        val resizeWidth = (width * (size.toDouble / height)).toInt
        (size, (if (resizeWidth == 0) 1 else resizeWidth, size))
      }
    }.toMap
  }
}
