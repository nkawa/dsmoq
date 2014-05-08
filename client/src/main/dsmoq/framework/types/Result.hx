package dsmoq.framework.types;

/**
 * @author terurou
 */
enum Result<TSuccess> {
    Success(x: TSuccess);
    Failure(x: Error);
}