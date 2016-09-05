package dsmoq.maintenance.data.user

import org.joda.time.DateTime

case class SearchResultUser(
  id: String,
  name: String,
  fullname: String,
  mailAddress: String,
  organization: String,
  title: String,
  description: String,
  createdAt: DateTime,
  updatedAt: DateTime,
  disabled: Boolean
)
