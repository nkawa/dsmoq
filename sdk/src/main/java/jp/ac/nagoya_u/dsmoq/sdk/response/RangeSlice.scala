package jp.ac.nagoya_u.dsmoq.sdk.response

import scala.collection.JavaConverters._

case class RangeSlice[A] (
  private val summary: RangeSliceSummary,
  private val results: Seq[A]
) {
  def getSummary = summary
  def getResults = results.asJava
}

case class RangeSliceSummary(
  private val total: Int,
  private val count: Int = 20,
  private val offset: Int = 0
) {
  def getTotal = total
  def getCount = count
  def getOffset = offset
}