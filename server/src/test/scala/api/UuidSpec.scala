package api

import dsmoq.logic.StringUtil
import org.scalatest.FreeSpec

class UuidSpec extends FreeSpec {
  "Util test" - {
    "指定した文字列がUUIDである" in {
      val str = "de9edaff-1547-447d-9517-4f23472bf002"
      assert(StringUtil.isUUID(str))
    }

    "指定した文字列がUUIDでない" in {
      val str = "1-2-3-45"
      assert(!StringUtil.isUUID(str))
    }
  }
}
