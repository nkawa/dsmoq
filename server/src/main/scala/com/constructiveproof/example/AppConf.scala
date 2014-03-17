package com.constructiveproof.example

import com.typesafe.config.ConfigFactory

/**
 * Created by terurou on 14/03/17.
 */
object AppConf {
  private val conf = ConfigFactory.load

  val imageDir = conf.getString("dsmoq.image_dir")
  val fileDir = conf.getString("dsmoq.file_dir")

  val guestGroupId = conf.getString("dsmoq.guest_uuid")
}
