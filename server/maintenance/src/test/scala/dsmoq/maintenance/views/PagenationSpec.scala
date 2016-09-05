package dsmoq.maintenance.views

import org.scalatest.FreeSpec
import org.scalatest.Matchers._

class PagenationSpec extends FreeSpec {
  "current page" - {
    "limit 10" - {
      val values = Seq(
        0 -> 1,
        1 -> 2,
        9 -> 2,
        10 -> 2,
        11 -> 3,
        19 -> 3,
        20 -> 3,
        21 -> 4
      )
      for {
        (offset, page) <- values
      } {
        s"offset ${offset} is page ${page}" in {
          Pagenation.pageOf(offset, 10) should be(page)
        }
      }
    }
  }
  "min page" - {
    "limit 10" - {
      val values = Seq(
        0 -> 1,
        10 -> 1,
        20 -> 1,
        30 -> 2,
        40 -> 3,
        50 -> 4,
        60 -> 5,
        70 -> 6,
        80 -> 6,
        90 -> 6
      )
      for {
        (offset, page) <- values
        total <- Seq(95, 100)
      } {
        s"offset ${offset}, total ${total} is page ${page}" in {
          Pagenation.minPageOf(offset, 10, total) should be(page)
        }
      }
    }
    "limit 10, total 2" - {
      "offset 0 is page 1" in {
        Pagenation.minPageOf(0, 10, 2) should be(1)
      }
    }
  }
  "max page" - {
    "limit 10" - {
      val values = Seq(
        0 -> 5,
        10 -> 5,
        20 -> 5,
        30 -> 6,
        40 -> 7,
        50 -> 8,
        60 -> 9,
        70 -> 10,
        80 -> 10,
        90 -> 10
      )
      for {
        (offset, page) <- values
        total <- Seq(95, 100)
      } {
        s"offset ${offset}, total ${total} is page ${page}" in {
          Pagenation.maxPageOf(offset, 10, total) should be(page)
        }
      }
    }
    "limit 10, total 2" - {
      "offset 0 is page 1" in {
        Pagenation.maxPageOf(0, 10, 2) should be(1)
      }
    }
  }
}
