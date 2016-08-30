package dsmoq.logic

/**
 * Identity Functor
 *
 * @tparam T type
 * @param get value
 */
case class Identity[T](get: T) {
  /**
   * Apply the given function f to the value.
   *
   * @tparam R return type
   * @param f the function to apply
   * @return the result of applying the given function f
   */
  def map[R](f: T => R): Identity[R] = Identity(f(get))
}
