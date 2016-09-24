package dsmoq.maintenance.views

import org.scalatest.FreeSpec
import org.scalatest.Matchers._

class PagenationSpec extends FreeSpec {
  "pagenation" - {
    "page 0, lastPage 10, total 100" in {
      val pagenation = Pagenation(0, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, false),
        Pagenation.Page(0, false),
        Seq(
          Pagenation.Page(1, false),
          Pagenation.Page(2, true),
          Pagenation.Page(3, true),
          Pagenation.Page(4, true),
          Pagenation.Page(5, true)
        ),
        Pagenation.Page(2, true),
        Pagenation.Page(10, true)
      )
      pagenation should be(expected)
    }
    "page 1, lastPage 10, total 100" in {
      val pagenation = Pagenation(1, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, false),
        Pagenation.Page(0, false),
        Seq(
          Pagenation.Page(1, false),
          Pagenation.Page(2, true),
          Pagenation.Page(3, true),
          Pagenation.Page(4, true),
          Pagenation.Page(5, true)
        ),
        Pagenation.Page(2, true),
        Pagenation.Page(10, true)
      )
      pagenation should be(expected)
    }
    "page 2, lastPage 10, total 100" in {
      val pagenation = Pagenation(2, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, true),
        Pagenation.Page(1, true),
        Seq(
          Pagenation.Page(1, true),
          Pagenation.Page(2, false),
          Pagenation.Page(3, true),
          Pagenation.Page(4, true),
          Pagenation.Page(5, true)
        ),
        Pagenation.Page(3, true),
        Pagenation.Page(10, true)
      )
      pagenation should be(expected)
    }
    "page 5, lastPage 10, total 100" in {
      val pagenation = Pagenation(5, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, true),
        Pagenation.Page(4, true),
        Seq(
          Pagenation.Page(3, true),
          Pagenation.Page(4, true),
          Pagenation.Page(5, false),
          Pagenation.Page(6, true),
          Pagenation.Page(7, true)
        ),
        Pagenation.Page(6, true),
        Pagenation.Page(10, true)
      )
      pagenation should be(expected)
    }
    "page 10, lastPage 10, total 100" in {
      val pagenation = Pagenation(10, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, true),
        Pagenation.Page(9, true),
        Seq(
          Pagenation.Page(6, true),
          Pagenation.Page(7, true),
          Pagenation.Page(8, true),
          Pagenation.Page(9, true),
          Pagenation.Page(10, false)
        ),
        Pagenation.Page(11, false),
        Pagenation.Page(10, false)
      )
      pagenation should be(expected)
    }
    "page 11, lastPage 10, total 100" in {
      val pagenation = Pagenation(11, 10, 100)
      val expected = Pagenation(
        Pagenation.Page(1, true),
        Pagenation.Page(9, true),
        Seq(
          Pagenation.Page(6, true),
          Pagenation.Page(7, true),
          Pagenation.Page(8, true),
          Pagenation.Page(9, true),
          Pagenation.Page(10, false)
        ),
        Pagenation.Page(11, false),
        Pagenation.Page(10, false)
      )
      pagenation should be(expected)
    }
    "page 1, lastPage 1, total 9" in {
      val pagenation = Pagenation(1, 1, 9)
      val expected = Pagenation(
        Pagenation.Page(1, false),
        Pagenation.Page(0, false),
        Seq(
          Pagenation.Page(1, false)
        ),
        Pagenation.Page(2, false),
        Pagenation.Page(1, false)
      )
      pagenation should be(expected)
    }
  }
}
