package dsmoq.sdk.response

case class User(
                 id: String,
                 name: String,
                 fullname: String,
                 organization: String,
                 title: String,
                 image: String,
                 mailAddress: String,
                 description: String,
                 isGuest: Boolean,
                 isDeleted: Boolean
                 )