package dsmoq.services.data

object SuggestData {
  case class User(
                          dataType: Int,
                          id: String,
                          name: String,
                          fullname: String,
                          organization: String,
                          image: String
                          )
  case class Group(
                           dataType: Int,
                           id: String,
                           name: String,
                           image: String
                           )
}
