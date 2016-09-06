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
        31 -> 3,
        40 -> 3,
        50 -> 4,
        60 -> 5,
        61 -> 6,
        70 -> 6,
        71 -> 7,
        80 -> 6,
        81 -> 7,
        90 -> 6,
        91 -> 7
      )
      for {
        total <- Seq(100, 92)
        (offset, page) <- values
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
        21 -> 6,
        30 -> 6,
        40 -> 7,
        50 -> 8,
        60 -> 9,
        61 -> 10,
        70 -> 10,
        71 -> 11,
        80 -> 10,
        81 -> 11,
        90 -> 10,
        91 -> 11
      )
      for {
        total <- Seq(100, 92)
        (offset, page) <- values
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

  "pagenation" - {
    "offset 0, limit 10, total 100" in {
      val pagenation = Pagenation(0, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, 0, false),
        Pagenation.Page(0, 0, false),
        Seq(
          Pagenation.Page(1, 0, false),
          Pagenation.Page(2, 10, true),
          Pagenation.Page(3, 20, true),
          Pagenation.Page(4, 30, true),
          Pagenation.Page(5, 40, true)
        ),
        Pagenation.Page(2, 10, true),
        Pagenation.Page(10, 90, true)
      )
      pagenation should be(expected)
    }
    "offset 1, limit 10, total 100" in {
      val pagenation = Pagenation(1, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, 0, true),
        Pagenation.Page(1, 0, true),
        Seq(
          Pagenation.Page(1, 0, true),
          Pagenation.Page(2, 1, false),
          Pagenation.Page(3, 11, true),
          Pagenation.Page(4, 21, true),
          Pagenation.Page(5, 31, true)
        ),
        Pagenation.Page(3, 11, true),
        Pagenation.Page(11, 91, true)
      )
      pagenation should be(expected)
    }
    "offset 71, limit 10, total 100" in {
      val pagenation = Pagenation(71, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, 0, true),
        Pagenation.Page(8, 61, true),
        Seq(
          Pagenation.Page(7, 51, true),
          Pagenation.Page(8, 61, true),
          Pagenation.Page(9, 71, false),
          Pagenation.Page(10, 81, true),
          Pagenation.Page(11, 91, true)
        ),
        Pagenation.Page(10, 81, true),
        Pagenation.Page(11, 91, true)
      )
      pagenation should be(expected)
    }
  }
}
