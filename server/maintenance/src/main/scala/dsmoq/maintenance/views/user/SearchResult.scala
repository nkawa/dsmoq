package dsmoq.maintenance.views.user

import org.joda.time.DateTime

case class SearchResult(
  from: Int,
  to: Int,
  total: Int,
  data: Seq[User]
)

case class User(
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
