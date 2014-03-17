package com.constructiveproof.example

import com.typesafe.config.ConfigFactory

/**
 * Created by terurou on 14/03/17.
 */
object AppConf {
  private val conf = ConfigFactory.load

  val guestGroupId = conf.getString("dsmoq.guest_uuid")
}
