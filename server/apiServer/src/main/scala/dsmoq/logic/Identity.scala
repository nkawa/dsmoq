package dsmoq.logic

case class Identity[T](get: T) {
  def map[R](f: T => R): Identity[R] = Identity(f(get))
}
